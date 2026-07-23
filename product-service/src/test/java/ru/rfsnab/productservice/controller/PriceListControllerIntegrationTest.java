package ru.rfsnab.productservice.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;
import ru.rfsnab.productservice.model.Category;
import ru.rfsnab.productservice.model.Product;
import ru.rfsnab.productservice.model.PriceListStatus;
import ru.rfsnab.productservice.repository.CategoryRepository;
import ru.rfsnab.productservice.repository.PriceListRequestRepository;
import ru.rfsnab.productservice.repository.ProductRepository;
import ru.rfsnab.productservice.service.CategoryService;
import ru.rfsnab.productservice.service.StorageService;

import java.math.BigDecimal;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Полный цикл: POST → Kafka → consumer → generate() → READY → download.
 * StorageService замокан, чтобы не ходить в реальный S3.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@SuppressWarnings("resource")
@DisplayName("PriceListController Integration Tests")
class PriceListControllerIntegrationTest {

    private static final PostgreSQLContainer<?> postgres;
    private static final KafkaContainer kafka;

    static {
        postgres = new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
                .withDatabaseName("product_pricelist_test_db")
                .withUsername("test")
                .withPassword("test");
        postgres.start();

        kafka = new KafkaContainer(DockerImageName.parse("apache/kafka-native:3.8.0"))
                .withEnv("KAFKA_AUTO_CREATE_TOPICS_ENABLE", "true");
        kafka.start();
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.PostgreSQLDialect");
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private CategoryService categoryService;

    @Autowired
    private PriceListRequestRepository priceListRequestRepository;

    @MockitoBean
    private StorageService storageService;

    private Category testCategory;

    @BeforeEach
    void setUp() {
        priceListRequestRepository.deleteAll();
        productRepository.deleteAll();
        categoryRepository.deleteAll();

        testCategory = categoryRepository.save(Category.builder()
                .name("Огнетушители")
                .slug("ognetushiteli-plist")
                .isActive(true)
                .displayOrder(1)
                .build());

        productRepository.save(Product.builder()
                .name("Огнетушитель ОП-4")
                .slug("ognetushitel-op-4-plist")
                .sku("ART-100")
                .unitOfMeasure("шт")
                .price(new BigDecimal("1500.00"))
                .wholesalePrice(new BigDecimal("1800.00"))
                .stockQuantity(100)
                .isActive(true)
                .category(testCategory)
                .build());

        categoryService.refreshCategoryTree();

        reset(storageService);
        when(storageService.fileExists(anyString())).thenReturn(true);
        doNothing().when(storageService).uploadBytes(any(byte[].class), anyString(), anyString());
    }

    @Test
    @WithMockUser
    @DisplayName("полный цикл: POST -> PENDING -> consumer -> READY -> download владельцем; чужому -> 404")
    void fullCycle_CreateGenerateDownload() throws Exception {
        String body = objectMapper.writeValueAsString(new CategoryIdsBody(java.util.List.of(testCategory.getId())));

        String response = mockMvc.perform(post("/api/v1/price-lists")
                        .header("X-User-Id", "10")
                        .header("X-Client-Type", "B2B")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status", is("PENDING")))
                .andReturn().getResponse().getContentAsString();

        Long requestId = objectMapper.readTree(response).get("id").asLong();

        await().atMost(15, TimeUnit.SECONDS).pollInterval(200, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> {
                    var saved = priceListRequestRepository.findById(requestId).orElseThrow();
                    assertThat(saved.getStatus()).isEqualTo(PriceListStatus.READY);
                    assertThat(saved.getFileKey()).isNotBlank();
                    assertThat(saved.getRowCount()).isEqualTo(1);
                });

        when(storageService.downloadStream(anyString()))
                .thenReturn(fakeXlsxStream());

        mockMvc.perform(get("/api/v1/price-lists/{id}/download", requestId)
                        .header("X-User-Id", "10"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString(".xlsx")));

        mockMvc.perform(get("/api/v1/price-lists/{id}/download", requestId)
                        .header("X-User-Id", "999"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser
    @DisplayName("повторный create при существующем PENDING -> 422")
    void create_SecondWhilePending_Returns422() throws Exception {
        // делаем downloadStream/сохранение "медленным" не требуется — просто быстро создаём второй, пока первый ещё PENDING
        // используем существующую запись PENDING напрямую в БД, чтобы не зависеть от скорости consumer
        var pending = ru.rfsnab.productservice.model.PriceListRequest.builder()
                .userId(10L).clientType("B2B").categoryIds(java.util.List.of(testCategory.getId()))
                .status(PriceListStatus.PENDING).createdAt(java.time.LocalDateTime.now())
                .build();
        priceListRequestRepository.save(pending);

        String body = objectMapper.writeValueAsString(new CategoryIdsBody(java.util.List.of(testCategory.getId())));

        mockMvc.perform(post("/api/v1/price-lists")
                        .header("X-User-Id", "10")
                        .header("X-Client-Type", "B2B")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnprocessableEntity());
    }

    private software.amazon.awssdk.core.ResponseInputStream<software.amazon.awssdk.services.s3.model.GetObjectResponse> fakeXlsxStream() {
        byte[] data = "fake-xlsx-content".getBytes();
        return new software.amazon.awssdk.core.ResponseInputStream<>(
                software.amazon.awssdk.services.s3.model.GetObjectResponse.builder().build(),
                new java.io.ByteArrayInputStream(data));
    }

    private record CategoryIdsBody(java.util.List<Long> categoryIds) {
    }
}
