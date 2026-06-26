package ru.rfsnab.integrationservice.service.ftk;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import ru.rfsnab.integrationservice.config.IntegrationProperties;
import ru.rfsnab.integrationservice.dto.BatchImportRequest;
import ru.rfsnab.integrationservice.dto.BatchImportResponse;
import ru.rfsnab.integrationservice.dto.ProductImportItemDto;
import ru.rfsnab.integrationservice.model.ImportLog;
import ru.rfsnab.integrationservice.model.ftk.FtkProduct;
import ru.rfsnab.integrationservice.model.ftk.FtkProduct.FtkVariant;
import ru.rfsnab.integrationservice.repository.ImportLogRepository;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("FtkImportService")
@ExtendWith(MockitoExtension.class)
class FtkImportServiceTest {

    @Mock private FtkXlsParser xlsParser;
    @Mock private FtkXmlParser xmlParser;
    @Mock private FtkFtpClient ftpClient;
    @Mock private FtkCategoryMapper categoryMapper;
    @Mock private FtkImageDownloader imageDownloader;
    @Mock private RestTemplate productServiceRestTemplate;
    @Mock private ImportLogRepository importLogRepository;

    private IntegrationProperties properties;
    private FtkImportService service;

    @BeforeEach
    void setUp() {
        properties = new IntegrationProperties();
        properties.getFtk().setImportLimit(0);
        properties.getProductService().setUrl("http://product-service:8083");
        properties.getImportConfig().setChunkSize(100);

        when(importLogRepository.save(any(ImportLog.class))).thenAnswer(inv -> inv.getArgument(0));

        service = new FtkImportService(
                xlsParser, xmlParser, ftpClient, categoryMapper, imageDownloader,
                productServiceRestTemplate, properties, importLogRepository
        );
    }

    /**
     * Строит FtkProduct с одним вариантом и заданными imagePaths.
     */
    private FtkProduct product(String article, String name, BigDecimal price,
                                String description, List<String> imagePaths,
                                List<FtkVariant> variants) {
        List<FtkVariant> effectiveVariants = variants.isEmpty()
                ? List.of(FtkVariant.builder()
                        .offerUuid(null).article(article).price(price)
                        .stockQuantity(0).attributes(Map.of()).vatRate(null)
                        .barcode(null).countryOfOrigin(null).deleted(false).build())
                : variants;
        return FtkProduct.builder()
                .productUuid(null)
                .article(article)
                .name(name)
                .description(description)
                .imagePaths(imagePaths != null ? imagePaths : List.of())
                .groupUuid("GRP-SPEC")
                .unitOfMeasure("шт")
                .properties(Map.of())
                .variants(effectiveVariants)
                .build();
    }

    private BatchImportResponse okResponse(int created, int updated) {
        BatchImportResponse r = new BatchImportResponse();
        r.setCreated(created);
        r.setUpdated(updated);
        r.setFailed(0);
        r.setTotalReceived(created + updated);
        return r;
    }

    @Nested
    @DisplayName("Маппинг полей")
    class MappingTests {

        @Test
        @DisplayName("description передаётся в DTO")
        void shouldMapDescription() throws IOException {
            FtkProduct p = product("12345", "Костюм", new BigDecimal("9267"),
                    "Полное описание костюма", null, List.of());
            when(xlsParser.parse(any())).thenReturn(List.of(p));
            when(categoryMapper.resolveCategory(anyString())).thenReturn(42L);
            when(productServiceRestTemplate.postForEntity(anyString(), any(), eq(BatchImportResponse.class)))
                    .thenReturn(ResponseEntity.ok(okResponse(1, 0)));

            service.importFromXls(InputStream.nullInputStream());

            ArgumentCaptor<BatchImportRequest> captor = ArgumentCaptor.forClass(BatchImportRequest.class);
            verify(productServiceRestTemplate).postForEntity(anyString(), captor.capture(), eq(BatchImportResponse.class));

            ProductImportItemDto dto = captor.getValue().getItems().get(0);
            assertThat(dto.getDescription()).isEqualTo("Полное описание костюма");
            assertThat(dto.getSource()).isEqualTo("FTK");
        }

        @Test
        @DisplayName("externalId = 'FTK-{article}'")
        void shouldBuildCorrectExternalId() throws IOException {
            FtkProduct p = product("87490974", "Товар", new BigDecimal("1000"),
                    null, null, List.of());
            when(xlsParser.parse(any())).thenReturn(List.of(p));
            when(categoryMapper.resolveCategory(anyString())).thenReturn(42L);
            when(productServiceRestTemplate.postForEntity(anyString(), any(), eq(BatchImportResponse.class)))
                    .thenReturn(ResponseEntity.ok(okResponse(1, 0)));

            service.importFromXls(InputStream.nullInputStream());

            ArgumentCaptor<BatchImportRequest> captor = ArgumentCaptor.forClass(BatchImportRequest.class);
            verify(productServiceRestTemplate).postForEntity(anyString(), captor.capture(), eq(BatchImportResponse.class));
            assertThat(captor.getValue().getItems().get(0).getExternalId()).isEqualTo("FTK-87490974");
        }

        @Test
        @DisplayName("wholesalePrice = price (у ФТК нет оптовой цены)")
        void shouldSetWholesalePriceEqualToPrice() throws IOException {
            BigDecimal price = new BigDecimal("4660");
            FtkProduct p = product("111", "Товар", price, null, null, List.of());
            when(xlsParser.parse(any())).thenReturn(List.of(p));
            when(categoryMapper.resolveCategory(anyString())).thenReturn(42L);
            when(productServiceRestTemplate.postForEntity(anyString(), any(), eq(BatchImportResponse.class)))
                    .thenReturn(ResponseEntity.ok(okResponse(1, 0)));

            service.importFromXls(InputStream.nullInputStream());

            ArgumentCaptor<BatchImportRequest> captor = ArgumentCaptor.forClass(BatchImportRequest.class);
            verify(productServiceRestTemplate).postForEntity(anyString(), captor.capture(), eq(BatchImportResponse.class));

            ProductImportItemDto dto = captor.getValue().getItems().get(0);
            assertThat(dto.getPrice()).isEqualByComparingTo(price);
            assertThat(dto.getWholesalePrice()).isEqualByComparingTo(price);
        }

        @Test
        @DisplayName("варианты маппятся с barcode и countryOfOrigin")
        void shouldMapVariantsWithBarcodeAndCountry() throws IOException {
            List<FtkVariant> variants = List.of(
                    FtkVariant.builder()
                            .offerUuid(null)
                            .article("87486380.001")
                            .price(new BigDecimal("4660"))
                            .stockQuantity(10)
                            .attributes(Map.of("Размер", "44-46", "Рост", "170-176"))
                            .vatRate(20)
                            .barcode("4607032891234")
                            .countryOfOrigin("Россия")
                            .deleted(false)
                            .build()
            );
            FtkProduct p = product("87486380", "Жилет", new BigDecimal("4660"),
                    null, null, variants);
            when(xlsParser.parse(any())).thenReturn(List.of(p));
            when(categoryMapper.resolveCategory(anyString())).thenReturn(42L);
            when(productServiceRestTemplate.postForEntity(anyString(), any(), eq(BatchImportResponse.class)))
                    .thenReturn(ResponseEntity.ok(okResponse(1, 0)));

            service.importFromXls(InputStream.nullInputStream());

            ArgumentCaptor<BatchImportRequest> captor = ArgumentCaptor.forClass(BatchImportRequest.class);
            verify(productServiceRestTemplate).postForEntity(anyString(), captor.capture(), eq(BatchImportResponse.class));

            ProductImportItemDto dto = captor.getValue().getItems().get(0);
            assertThat(dto.getVariants()).hasSize(1);
            ProductImportItemDto.VariantImportItemDto variant = dto.getVariants().get(0);
            assertThat(variant.getExternalId()).isEqualTo("FTK-87486380.001");
            assertThat(variant.getStockQuantity()).isEqualTo(10);
            assertThat(variant.getBarcode()).isEqualTo("4607032891234");
            assertThat(variant.getCountryOfOrigin()).isEqualTo("Россия");
            assertThat(variant.getAttributes())
                    .containsEntry("Размер", "44-46")
                    .containsEntry("Рост", "170-176");
        }

        @Test
        @DisplayName("unitOfMeasure передаётся в DTO")
        void shouldMapUnitOfMeasure() throws IOException {
            FtkProduct p = product("333", "Товар", BigDecimal.ONE, null, null, List.of());
            when(xlsParser.parse(any())).thenReturn(List.of(p));
            when(categoryMapper.resolveCategory(anyString())).thenReturn(42L);
            when(productServiceRestTemplate.postForEntity(anyString(), any(), eq(BatchImportResponse.class)))
                    .thenReturn(ResponseEntity.ok(okResponse(1, 0)));

            service.importFromXls(InputStream.nullInputStream());

            ArgumentCaptor<BatchImportRequest> captor = ArgumentCaptor.forClass(BatchImportRequest.class);
            verify(productServiceRestTemplate).postForEntity(anyString(), captor.capture(), eq(BatchImportResponse.class));
            assertThat(captor.getValue().getItems().get(0).getUnitOfMeasure()).isEqualTo("шт");
        }
    }

    @Nested
    @DisplayName("Лимит импорта")
    class LimitTests {

        @Test
        @DisplayName("лимит 0 — импортируются все товары")
        void shouldImportAllWhenLimitIsZero() throws IOException {
            properties.getFtk().setImportLimit(0);
            List<FtkProduct> products = List.of(
                    product("1", "Товар 1", BigDecimal.ONE, null, null, List.of()),
                    product("2", "Товар 2", BigDecimal.ONE, null, null, List.of()),
                    product("3", "Товар 3", BigDecimal.ONE, null, null, List.of())
            );
            when(xlsParser.parse(any())).thenReturn(products);
            when(categoryMapper.resolveCategory(anyString())).thenReturn(42L);
            when(productServiceRestTemplate.postForEntity(anyString(), any(), eq(BatchImportResponse.class)))
                    .thenReturn(ResponseEntity.ok(okResponse(3, 0)));

            FtkImportService.FtkImportResult result = service.importFromXls(InputStream.nullInputStream());
            assertThat(result.totalProducts()).isEqualTo(3);
        }

        @Test
        @DisplayName("лимит 2 — импортируются только первые 2 товара")
        void shouldRespectImportLimit() throws IOException {
            properties.getFtk().setImportLimit(2);
            List<FtkProduct> products = List.of(
                    product("1", "Товар 1", BigDecimal.ONE, null, null, List.of()),
                    product("2", "Товар 2", BigDecimal.ONE, null, null, List.of()),
                    product("3", "Товар 3", BigDecimal.ONE, null, null, List.of())
            );
            when(xlsParser.parse(any())).thenReturn(products);
            when(categoryMapper.resolveCategory(anyString())).thenReturn(42L);
            when(productServiceRestTemplate.postForEntity(anyString(), any(), eq(BatchImportResponse.class)))
                    .thenReturn(ResponseEntity.ok(okResponse(2, 0)));

            FtkImportService.FtkImportResult result = service.importFromXls(InputStream.nullInputStream());
            assertThat(result.totalProducts()).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("Изображения")
    class ImageTests {

        @Test
        @DisplayName("все imagePaths загружаются для одного товара")
        void shouldDownloadAllImagePaths() throws IOException {
            FtkProduct p = product("111", "Товар", BigDecimal.ONE,
                    null, List.of("img/img1.jpg", "img/img2.jpg"), List.of());
            when(xlsParser.parse(any())).thenReturn(List.of(p));
            when(categoryMapper.resolveCategory(anyString())).thenReturn(42L);
            when(productServiceRestTemplate.postForEntity(anyString(), any(), eq(BatchImportResponse.class)))
                    .thenReturn(ResponseEntity.ok(okResponse(1, 0)));
            when(imageDownloader.downloadAndUpload(anyString(), anyString())).thenReturn(true);

            FtkImportService.FtkImportResult result = service.importFromXls(InputStream.nullInputStream());

            verify(imageDownloader, times(2)).downloadAndUpload(anyString(), eq("FTK-111"));
            assertThat(result.imagesOk()).isEqualTo(2);
            assertThat(result.imagesFailed()).isEqualTo(0);
        }

        @Test
        @DisplayName("изображение не загружается если imagePaths пуст")
        void shouldSkipImageWhenPathsEmpty() throws IOException {
            FtkProduct p = product("222", "Товар", BigDecimal.ONE, null, List.of(), List.of());
            when(xlsParser.parse(any())).thenReturn(List.of(p));
            when(categoryMapper.resolveCategory(anyString())).thenReturn(42L);
            when(productServiceRestTemplate.postForEntity(anyString(), any(), eq(BatchImportResponse.class)))
                    .thenReturn(ResponseEntity.ok(okResponse(1, 0)));

            FtkImportService.FtkImportResult result = service.importFromXls(InputStream.nullInputStream());

            verify(imageDownloader, never()).downloadAndUpload(anyString(), anyString());
            assertThat(result.imagesOk()).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("Результат импорта")
    class ResultTests {

        @Test
        @DisplayName("пустой XLS → 0 товаров, нет вызовов product-service")
        void shouldReturnZeroResultForEmptyXls() throws IOException {
            when(xlsParser.parse(any())).thenReturn(List.of());

            FtkImportService.FtkImportResult result = service.importFromXls(InputStream.nullInputStream());

            assertThat(result.totalProducts()).isEqualTo(0);
            assertThat(result.created()).isEqualTo(0);
            verify(productServiceRestTemplate, never()).postForEntity(anyString(), any(), any());
        }

        @Test
        @DisplayName("ошибка product-service → failed увеличивается")
        void shouldCountFailedOnProductServiceError() throws IOException {
            FtkProduct p = product("111", "Товар", BigDecimal.ONE, null, null, List.of());
            when(xlsParser.parse(any())).thenReturn(List.of(p));
            when(categoryMapper.resolveCategory(anyString())).thenReturn(42L);
            when(productServiceRestTemplate.postForEntity(anyString(), any(), eq(BatchImportResponse.class)))
                    .thenThrow(new RuntimeException("Connection refused"));

            FtkImportService.FtkImportResult result = service.importFromXls(InputStream.nullInputStream());

            assertThat(result.failed()).isEqualTo(1);
            assertThat(result.created()).isEqualTo(0);
        }
    }
}
