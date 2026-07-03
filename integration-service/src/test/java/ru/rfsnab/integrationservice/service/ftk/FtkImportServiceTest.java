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
import ru.rfsnab.integrationservice.service.ftk.FtkXmlParser.ClassifierData;
import ru.rfsnab.integrationservice.service.ftk.FtkXmlParser.OfferData;
import ru.rfsnab.integrationservice.service.ftk.FtkXmlParser.ProductData;
import ru.rfsnab.integrationservice.service.ftk.FtkXmlParser.RestData;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
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

        // lenient: тест чекпоинта переопределяет save своим Answer
        org.mockito.Mockito.lenient()
                .when(importLogRepository.save(any(ImportLog.class))).thenAnswer(inv -> inv.getArgument(0));

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
        @DisplayName("отрицательный stockQuantity зажимается в 0 (иначе БД check violation)")
        void shouldClampNegativeStockQuantityToZero() throws IOException {
            List<FtkVariant> variants = List.of(
                    FtkVariant.builder()
                            .offerUuid(null).article("999.001").price(BigDecimal.ONE)
                            .stockQuantity(-5)
                            .attributes(Map.of()).vatRate(null)
                            .barcode(null).countryOfOrigin(null).deleted(false).build()
            );
            FtkProduct p = product("999", "Товар с отрицат. остатком",
                    BigDecimal.ONE, null, null, variants);
            when(xlsParser.parse(any())).thenReturn(List.of(p));
            when(categoryMapper.resolveCategory(anyString())).thenReturn(42L);
            when(productServiceRestTemplate.postForEntity(anyString(), any(), eq(BatchImportResponse.class)))
                    .thenReturn(ResponseEntity.ok(okResponse(1, 0)));

            service.importFromXls(InputStream.nullInputStream());

            ArgumentCaptor<BatchImportRequest> captor = ArgumentCaptor.forClass(BatchImportRequest.class);
            verify(productServiceRestTemplate).postForEntity(anyString(), captor.capture(), eq(BatchImportResponse.class));

            ProductImportItemDto dto = captor.getValue().getItems().get(0);
            assertThat(dto.getVariants().get(0).getStockQuantity()).isZero();
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

    @Nested
    @DisplayName("importFromFtp — три порции")
    class FtpThreePartsTests {

        private final ClassifierData classifier =
                new ClassifierData(Map.of(), Map.of(), Map.of(), Map.of());

        private InputStream stream() {
            return new ByteArrayInputStream(new byte[0]);
        }

        private FtkProduct partProduct(String article, int part) {
            return FtkProduct.builder()
                    .productUuid(article)
                    .article(article)
                    .name("Товар " + article)
                    .description(null)
                    .imagePaths(List.of())
                    .groupUuid("GRP")
                    .unitOfMeasure("шт")
                    .partNumber(part)
                    .properties(Map.of())
                    .variants(List.of(FtkVariant.builder()
                            .offerUuid(null).article(article).price(BigDecimal.ONE)
                            .stockQuantity(0).attributes(Map.of()).vatRate(null)
                            .barcode(null).countryOfOrigin(null).deleted(false).build()))
                    .build();
        }

        /** Настраивает ftpClient/xmlParser так, чтобы порция part была полностью читаема и собиралась в partProducts. */
        private void mockFullPart(int part, List<FtkProduct> partProducts) throws Exception {
            String goodsDir = "/webdata/000000003/goods/" + part + "/";
            when(ftpClient.getGoodsDir(part)).thenReturn(goodsDir);
            when(ftpClient.findFileByPrefix(eq(goodsDir), eq("import___"))).thenReturn(goodsDir + "import___1.xml");
            when(ftpClient.findFileByPrefix(eq(goodsDir), eq("offers___"))).thenReturn(goodsDir + "offers___1.xml");
            when(ftpClient.findFileByPrefix(eq(goodsDir), eq("prices___"))).thenReturn(goodsDir + "prices___1.xml");
            when(ftpClient.findFileByPrefix(eq(goodsDir), eq("rests___"))).thenReturn(goodsDir + "rests___1.xml");

            when(ftpClient.openStream(goodsDir + "import___1.xml")).thenReturn(stream());
            when(ftpClient.openStream(goodsDir + "offers___1.xml")).thenReturn(stream());
            when(ftpClient.openStream(goodsDir + "rests___1.xml")).thenReturn(stream());
            FtkFtpClient.FtpStreamHandle handle = mock(FtkFtpClient.FtpStreamHandle.class);
            when(handle.getStream()).thenReturn(stream());
            when(ftpClient.openLargeStream(goodsDir + "prices___1.xml")).thenReturn(handle);

            when(xmlParser.assemble(any(), any(), any(), any(), eq(classifier), eq(part)))
                    .thenReturn(partProducts);
        }

        @Test
        @DisplayName("обходит все 3 порции и суммирует результаты сборки")
        void shouldProcessAllThreePartsAndAccumulateResults() throws Exception {
            when(ftpClient.getRootDir()).thenReturn("/webdata/000000003/");
            when(ftpClient.findFileByPrefix("/webdata/000000003/", "import___"))
                    .thenReturn("/webdata/000000003/import___1.xml");
            when(ftpClient.openStream("/webdata/000000003/import___1.xml")).thenReturn(stream());
            when(xmlParser.parseClassifier(any())).thenReturn(classifier);

            mockFullPart(1, List.of(partProduct("1", 1)));
            mockFullPart(2, List.of(partProduct("2", 2), partProduct("3", 2)));
            mockFullPart(3, List.of(partProduct("4", 3)));

            when(categoryMapper.resolveCategory(anyString())).thenReturn(42L);
            when(productServiceRestTemplate.postForEntity(anyString(), any(), eq(BatchImportResponse.class)))
                    .thenReturn(ResponseEntity.ok(okResponse(4, 0)));

            FtkImportService.FtkImportResult result = service.doImportFromFtp();

            assertThat(result.totalProducts()).isEqualTo(4);
            verify(xmlParser).assemble(any(), any(), any(), any(), eq(classifier), eq(1));
            verify(xmlParser).assemble(any(), any(), any(), any(), eq(classifier), eq(2));
            verify(xmlParser).assemble(any(), any(), any(), any(), eq(classifier), eq(3));
        }

        @Test
        @DisplayName("картинка с уже существующим fileKey не перекачивается — imagesSkipped++")
        void shouldSkipImageAlreadyUploaded() throws Exception {
            when(ftpClient.getRootDir()).thenReturn("/webdata/000000003/");
            when(ftpClient.findFileByPrefix("/webdata/000000003/", "import___"))
                    .thenReturn("/webdata/000000003/import___1.xml");
            when(ftpClient.openStream("/webdata/000000003/import___1.xml")).thenReturn(stream());
            when(xmlParser.parseClassifier(any())).thenReturn(classifier);

            FtkProduct productWithImage = FtkProduct.builder()
                    .productUuid("1").article("1").name("Товар 1").description(null)
                    .imagePaths(List.of("img/photo1.jpg"))
                    .groupUuid("GRP").unitOfMeasure("шт").partNumber(1)
                    .properties(Map.of())
                    .variants(List.of(FtkVariant.builder()
                            .offerUuid(null).article("1").price(BigDecimal.ONE)
                            .stockQuantity(0).attributes(Map.of()).vatRate(null)
                            .barcode(null).countryOfOrigin(null).deleted(false).build()))
                    .build();

            mockFullPart(1, List.of(productWithImage));
            mockFullPart(2, List.of());
            mockFullPart(3, List.of());

            when(categoryMapper.resolveCategory(anyString())).thenReturn(42L);
            when(productServiceRestTemplate.postForEntity(anyString(), any(), eq(BatchImportResponse.class)))
                    .thenReturn(ResponseEntity.ok(okResponse(1, 0)));

            String predictedFileKey = "products/ftk/FTK-1/ftk-photo1.webp";
            when(imageDownloader.predictFileKey(anyString(), eq("FTK-1"))).thenReturn(predictedFileKey);
            when(imageDownloader.getExistingFileKeysBatch(anyList()))
                    .thenReturn(Map.of("FTK-1", java.util.Set.of(predictedFileKey)));

            FtkImportService.FtkImportResult result = service.doImportFromFtp();

            verify(imageDownloader, never()).downloadAndUpload(anyString(), anyString());
            assertThat(result.imagesSkipped()).isEqualTo(1);
            assertThat(result.imagesOk()).isEqualTo(0);
        }

        @Test
        @DisplayName("создаёт запись IN_PROGRESS в начале и финализирует её же в SUCCESS")
        void shouldCreateInProgressLogAndFinalizeSameRow() throws Exception {
            when(ftpClient.getRootDir()).thenReturn("/webdata/000000003/");
            when(ftpClient.findFileByPrefix("/webdata/000000003/", "import___"))
                    .thenReturn("/webdata/000000003/import___1.xml");
            when(ftpClient.openStream("/webdata/000000003/import___1.xml")).thenReturn(stream());
            when(xmlParser.parseClassifier(any())).thenReturn(classifier);

            mockFullPart(1, List.of(partProduct("1", 1)));
            mockFullPart(2, List.of());
            mockFullPart(3, List.of());

            when(categoryMapper.resolveCategory(anyString())).thenReturn(42L);
            when(productServiceRestTemplate.postForEntity(anyString(), any(), eq(BatchImportResponse.class)))
                    .thenReturn(ResponseEntity.ok(okResponse(1, 0)));

            // фиксируем статус на момент каждого save — объект мутируется, пост-фактум капчер не годится
            List<ImportLog.ImportStatus> statusesAtSave = new java.util.ArrayList<>();
            List<ImportLog> savedInstances = new java.util.ArrayList<>();
            when(importLogRepository.save(any(ImportLog.class))).thenAnswer(inv -> {
                ImportLog entry = inv.getArgument(0);
                statusesAtSave.add(entry.getStatus());
                savedInstances.add(entry);
                return entry;
            });

            service.doImportFromFtp(2);

            assertThat(statusesAtSave).containsExactly(
                    ImportLog.ImportStatus.IN_PROGRESS, ImportLog.ImportStatus.SUCCESS);
            assertThat(savedInstances.get(1)).isSameAs(savedInstances.get(0));
            assertThat(savedInstances.get(0).getResumeAttempts()).isEqualTo(2);
            assertThat(savedInstances.get(0).getCompletedAt()).isNotNull();
        }

        @Test
        @DisplayName("отсутствие файла в одной порции не валит весь импорт — остальные обрабатываются")
        void shouldSkipPartWhenFileMissingButProcessOthers() throws Exception {
            when(ftpClient.getRootDir()).thenReturn("/webdata/000000003/");
            when(ftpClient.findFileByPrefix("/webdata/000000003/", "import___"))
                    .thenReturn("/webdata/000000003/import___1.xml");
            when(ftpClient.openStream("/webdata/000000003/import___1.xml")).thenReturn(stream());
            when(xmlParser.parseClassifier(any())).thenReturn(classifier);

            // Порция 2: import___ не найден на FTP
            String goodsDir2 = "/webdata/000000003/goods/2/";
            when(ftpClient.getGoodsDir(2)).thenReturn(goodsDir2);
            when(ftpClient.findFileByPrefix(eq(goodsDir2), eq("import___"))).thenReturn(null);

            mockFullPart(1, List.of(partProduct("1", 1)));
            mockFullPart(3, List.of(partProduct("3", 3)));

            when(categoryMapper.resolveCategory(anyString())).thenReturn(42L);
            when(productServiceRestTemplate.postForEntity(anyString(), any(), eq(BatchImportResponse.class)))
                    .thenReturn(ResponseEntity.ok(okResponse(2, 0)));

            FtkImportService.FtkImportResult result = service.doImportFromFtp();

            assertThat(result.totalProducts()).isEqualTo(2);
            verify(xmlParser, never()).assemble(any(), any(), any(), any(), eq(classifier), eq(2));
            verify(xmlParser).assemble(any(), any(), any(), any(), eq(classifier), eq(1));
            verify(xmlParser).assemble(any(), any(), any(), any(), eq(classifier), eq(3));
        }
    }
}
