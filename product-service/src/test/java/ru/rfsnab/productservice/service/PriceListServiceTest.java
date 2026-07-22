package ru.rfsnab.productservice.service;

import jakarta.persistence.EntityManager;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import ru.rfsnab.productservice.dto.PriceListRequested;
import ru.rfsnab.productservice.dto.PriceListResponse;
import ru.rfsnab.productservice.exception.CategoryValidationException;
import ru.rfsnab.productservice.exception.PriceListDispatchException;
import ru.rfsnab.productservice.exception.PriceListNotFoundException;
import ru.rfsnab.productservice.exception.PriceListNotReadyException;
import ru.rfsnab.productservice.exception.PriceListPendingException;
import ru.rfsnab.productservice.model.PriceListRequest;
import ru.rfsnab.productservice.model.PriceListStatus;
import ru.rfsnab.productservice.model.Product;
import ru.rfsnab.productservice.repository.PriceListRequestRepository;
import ru.rfsnab.productservice.repository.ProductRepository;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PriceListService Unit Tests")
class PriceListServiceTest {

    @Mock
    private PriceListRequestRepository priceListRequestRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private CategoryService categoryService;

    @Mock
    private StorageService storageService;

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Mock
    private EntityManager entityManager;

    private PriceListService priceListService;

    @BeforeEach
    void setUp() {
        priceListService = new PriceListService(
                priceListRequestRepository, productRepository, categoryService, storageService, kafkaTemplate,
                entityManager);
    }

    // ==================== create() ====================

    @Nested
    @DisplayName("create()")
    class CreateTests {

        @Test
        @DisplayName("успешно создаёт запрос и отправляет Kafka-событие")
        void create_ValidRequest_Success() throws Exception {
            when(categoryService.existsById(1L)).thenReturn(true);
            when(priceListRequestRepository.existsByUserIdAndStatus(10L, PriceListStatus.PENDING)).thenReturn(false);
            when(priceListRequestRepository.saveAndFlush(any(PriceListRequest.class))).thenAnswer(inv -> {
                PriceListRequest r = inv.getArgument(0);
                r.setId(100L);
                return r;
            });
            when(categoryService.getCategoryNameById(1L)).thenReturn("Огнетушители");

            CompletableFuture<SendResult<String, Object>> future = CompletableFuture.completedFuture(null);
            when(kafkaTemplate.send(eq(PriceListService.TOPIC_PRICE_LIST_REQUESTS), any(PriceListRequested.class)))
                    .thenReturn(future);

            PriceListResponse response = priceListService.create(10L, "B2B", List.of(1L));

            assertThat(response.id()).isEqualTo(100L);
            assertThat(response.status()).isEqualTo("PENDING");
            assertThat(response.categoryNames()).containsExactly("Огнетушители");
            verify(kafkaTemplate).send(eq(PriceListService.TOPIC_PRICE_LIST_REQUESTS), any(PriceListRequested.class));
        }

        @Test
        @DisplayName("выбрасывает 400 для несуществующей категории")
        void create_CategoryNotFound_ThrowsException() {
            when(categoryService.existsById(999L)).thenReturn(false);

            assertThatThrownBy(() -> priceListService.create(10L, "B2B", List.of(999L)))
                    .isInstanceOf(CategoryValidationException.class);

            verifyNoInteractions(kafkaTemplate);
        }

        @Test
        @DisplayName("выбрасывает 422, если у пользователя уже есть PENDING запрос")
        void create_AlreadyPending_ThrowsException() {
            when(categoryService.existsById(1L)).thenReturn(true);
            when(priceListRequestRepository.existsByUserIdAndStatus(10L, PriceListStatus.PENDING)).thenReturn(true);

            assertThatThrownBy(() -> priceListService.create(10L, "B2B", List.of(1L)))
                    .isInstanceOf(PriceListPendingException.class);

            verifyNoInteractions(kafkaTemplate);
        }

        @Test
        @DisplayName("гонка: DataIntegrityViolationException на saveAndFlush транслируется в 422")
        void create_ConcurrentPendingRace_TranslatesToPriceListPendingException() {
            when(categoryService.existsById(1L)).thenReturn(true);
            when(priceListRequestRepository.existsByUserIdAndStatus(10L, PriceListStatus.PENDING)).thenReturn(false);
            when(priceListRequestRepository.saveAndFlush(any(PriceListRequest.class)))
                    .thenThrow(new DataIntegrityViolationException("ux_price_list_one_pending"));

            assertThatThrownBy(() -> priceListService.create(10L, "B2B", List.of(1L)))
                    .isInstanceOf(PriceListPendingException.class);

            verifyNoInteractions(kafkaTemplate);
        }

        @Test
        @DisplayName("при таймауте отправки в Kafka удаляет запись и выбрасывает 503")
        void create_KafkaTimeout_DeletesRequestAndThrows503() throws Exception {
            when(categoryService.existsById(1L)).thenReturn(true);
            when(priceListRequestRepository.existsByUserIdAndStatus(10L, PriceListStatus.PENDING)).thenReturn(false);
            when(priceListRequestRepository.saveAndFlush(any(PriceListRequest.class))).thenAnswer(inv -> {
                PriceListRequest r = inv.getArgument(0);
                r.setId(200L);
                return r;
            });

            CompletableFuture<SendResult<String, Object>> failedFuture = new CompletableFuture<>();
            failedFuture.completeExceptionally(new TimeoutException("timeout"));
            when(kafkaTemplate.send(eq(PriceListService.TOPIC_PRICE_LIST_REQUESTS), any(PriceListRequested.class)))
                    .thenReturn(failedFuture);

            assertThatThrownBy(() -> priceListService.create(10L, "B2B", List.of(1L)))
                    .isInstanceOf(PriceListDispatchException.class);

            verify(priceListRequestRepository).deleteById(200L);
        }
    }

    // ==================== download() ====================

    @Nested
    @DisplayName("download()")
    class DownloadTests {

        @Test
        @DisplayName("выбрасывает 404 для чужого запроса (не раскрывая существование)")
        void download_NotOwner_Throws404() {
            PriceListRequest request = PriceListRequest.builder()
                    .id(1L).userId(999L).status(PriceListStatus.READY).fileKey("key").build();
            when(priceListRequestRepository.findById(1L)).thenReturn(java.util.Optional.of(request));

            assertThatThrownBy(() -> priceListService.download(10L, 1L))
                    .isInstanceOf(PriceListNotFoundException.class);
        }

        @Test
        @DisplayName("выбрасывает 409 если статус не READY")
        void download_NotReady_Throws409() {
            PriceListRequest request = PriceListRequest.builder()
                    .id(1L).userId(10L).status(PriceListStatus.PENDING).build();
            when(priceListRequestRepository.findById(1L)).thenReturn(java.util.Optional.of(request));

            assertThatThrownBy(() -> priceListService.download(10L, 1L))
                    .isInstanceOf(PriceListNotReadyException.class);
        }

        @Test
        @DisplayName("выбрасывает 404 если файл отсутствует в хранилище")
        void download_FileMissingInStorage_Throws404() {
            PriceListRequest request = PriceListRequest.builder()
                    .id(1L).userId(10L).status(PriceListStatus.READY).fileKey("price-lists/10/1.xlsx").build();
            when(priceListRequestRepository.findById(1L)).thenReturn(java.util.Optional.of(request));
            when(storageService.fileExists("price-lists/10/1.xlsx")).thenReturn(false);

            assertThatThrownBy(() -> priceListService.download(10L, 1L))
                    .isInstanceOf(PriceListNotFoundException.class);
        }
    }

    // ==================== generate() ====================

    @Nested
    @DisplayName("generate()")
    class GenerateTests {

        @Test
        @DisplayName("идемпотентность: пропускает генерацию, если статус уже READY")
        void generate_AlreadyReady_Skips() {
            PriceListRequest request = PriceListRequest.builder()
                    .id(1L).userId(10L).status(PriceListStatus.READY).categoryIds(List.of(1L)).build();
            when(priceListRequestRepository.findById(1L)).thenReturn(java.util.Optional.of(request));

            priceListService.generate(1L);

            verifyNoInteractions(productRepository, storageService);
            verify(priceListRequestRepository, never()).save(any());
        }

        @Test
        @DisplayName("запись не найдена (удалена гонкой при сбое Kafka) -> тихий возврат без исключения")
        void generate_RequestNotFound_ReturnsSilently() {
            when(priceListRequestRepository.findById(999L)).thenReturn(java.util.Optional.empty());

            priceListService.generate(999L);

            verifyNoInteractions(productRepository, storageService);
            verify(priceListRequestRepository, never()).save(any());
        }

        @Test
        @DisplayName("B2B клиент получает цену price, B2C — wholesalePrice; XLS содержит 4 колонки без остатка")
        void generate_B2BClient_UsesPriceColumn() throws IOException {
            PriceListRequest request = PriceListRequest.builder()
                    .id(1L).userId(10L).clientType("B2B").status(PriceListStatus.PENDING)
                    .categoryIds(List.of(1L)).createdAt(LocalDateTime.now()).build();
            when(priceListRequestRepository.findById(1L)).thenReturn(java.util.Optional.of(request));
            when(categoryService.getSubtreeCategoryIds(List.of(1L))).thenReturn(List.of(1L, 2L));

            Product product = Product.builder()
                    .id(1L).sku("ART-1").name("Товар 1").unitOfMeasure("шт")
                    .price(new BigDecimal("100.00")).wholesalePrice(new BigDecimal("120.00"))
                    .stockQuantity(50).isActive(true).build();

            Page<Product> page = new PageImpl<>(List.of(product), PageRequest.of(0, 1000), 1);
            when(productRepository.findByCategoryIdInAndIsActiveTrue(eq(List.of(1L, 2L)), any(Pageable.class)))
                    .thenReturn(page);

            ByteArrayOutputStream capturedBytes = new ByteArrayOutputStream();
            doAnswer(inv -> {
                byte[] data = inv.getArgument(0);
                capturedBytes.write(data);
                return null;
            }).when(storageService).uploadBytes(any(byte[].class), anyString(), anyString());

            priceListService.generate(1L);

            assertThat(request.getStatus()).isEqualTo(PriceListStatus.READY);
            assertThat(request.getRowCount()).isEqualTo(1);
            assertThat(request.getFileKey()).isEqualTo("price-lists/10/1.xlsx");

            try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(capturedBytes.toByteArray()))) {
                Sheet sheet = workbook.getSheetAt(0);

                Row header = sheet.getRow(0);
                assertThat(header.getLastCellNum()).isEqualTo((short) 4);
                assertThat(header.getCell(0).getStringCellValue()).isEqualTo("Артикул");
                assertThat(header.getCell(1).getStringCellValue()).isEqualTo("Наименование");
                assertThat(header.getCell(2).getStringCellValue()).isEqualTo("Ед.изм.");
                assertThat(header.getCell(3).getStringCellValue()).isEqualTo("Цена");

                Row dataRow = sheet.getRow(1);
                assertThat(dataRow.getCell(0).getStringCellValue()).isEqualTo("ART-1");
                assertThat(dataRow.getCell(1).getStringCellValue()).isEqualTo("Товар 1");
                assertThat(dataRow.getCell(2).getStringCellValue()).isEqualTo("шт");
                assertThat(dataRow.getCell(3).getStringCellValue()).isEqualTo("100.00"); // B2B -> price
            }
        }

        @Test
        @DisplayName("при ошибке генерации помечает запрос FAILED, не пробрасывая исключение наружу")
        void generate_Error_MarksFailed() {
            PriceListRequest request = PriceListRequest.builder()
                    .id(1L).userId(10L).clientType("B2B").status(PriceListStatus.PENDING)
                    .categoryIds(List.of(1L)).createdAt(LocalDateTime.now()).build();
            when(priceListRequestRepository.findById(1L)).thenReturn(java.util.Optional.of(request));
            when(categoryService.getSubtreeCategoryIds(List.of(1L)))
                    .thenThrow(new RuntimeException("category tree broken"));

            priceListService.generate(1L);

            assertThat(request.getStatus()).isEqualTo(PriceListStatus.FAILED);
            assertThat(request.getErrorMessage()).contains("category tree broken");
        }
    }

    // ==================== getMyRequests() ====================

    @Nested
    @DisplayName("getMyRequests()")
    class GetMyRequestsTests {

        @Test
        @DisplayName("возвращает список запросов, отсортированных по created_at DESC")
        void getMyRequests_ReturnsSortedList() {
            PriceListRequest r1 = PriceListRequest.builder()
                    .id(1L).userId(10L).status(PriceListStatus.READY).categoryIds(List.of(1L))
                    .createdAt(LocalDateTime.now()).build();
            when(priceListRequestRepository.findByUserIdOrderByCreatedAtDesc(10L)).thenReturn(List.of(r1));
            when(categoryService.getCategoryNameById(1L)).thenReturn("Категория");

            List<PriceListResponse> result = priceListService.getMyRequests(10L);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).id()).isEqualTo(1L);
            assertThat(result.get(0).categoryNames()).containsExactly("Категория");
        }
    }
}
