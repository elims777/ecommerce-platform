package ru.rfsnab.productservice.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import ru.rfsnab.productservice.dto.BatchProductImportRequest;
import ru.rfsnab.productservice.dto.ProductImportItem;
import ru.rfsnab.productservice.model.Category;
import ru.rfsnab.productservice.model.Product;
import ru.rfsnab.productservice.model.Warehouse;
import ru.rfsnab.productservice.repository.CategoryRepository;
import ru.rfsnab.productservice.repository.ProductRepository;
import ru.rfsnab.productservice.repository.ProductStockRepository;
import ru.rfsnab.productservice.repository.WarehouseRepository;
import ru.rfsnab.productservice.service.StorageService;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("ProductImportController")
class ProductImportControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private ProductRepository productRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private ProductStockRepository productStockRepository;
    @Autowired private WarehouseRepository warehouseRepository;

    @MockitoBean private StorageService storageService;

    private static final String WAREHOUSE_CODE = "SYKTYVKAR";

    private Warehouse warehouse;

    private BatchProductImportRequest batch(List<ProductImportItem> items) {
        return new BatchProductImportRequest(items, WAREHOUSE_CODE);
    }

    @BeforeEach
    void setUp() {
        productStockRepository.deleteAll();
        productRepository.deleteAll();
        categoryRepository.deleteAll();
        warehouseRepository.deleteAll();

        warehouse = warehouseRepository.save(Warehouse.builder()
                .code(WAREHOUSE_CODE).name("Основной склад").city("Сыктывкар").pickupAvailable(true).sortOrder(1).build());

        categoryRepository.save(Category.builder()
                .name("import-1c")
                .slug("import-1c")
                .isActive(true)
                .displayOrder(0)
                .build());
    }

    // Остатки ссылаются на products/warehouses — убираем, чтобы не ломать deleteAll() в соседних тестах
    @AfterEach
    void cleanStocks() {
        productStockRepository.deleteAll();
        productRepository.deleteAll();
        warehouseRepository.deleteAll();
    }

    @Test
    @WithMockUser
    @DisplayName("POST /import/batch — создаёт товар с description и material")
    void shouldImportProductWithDescriptionAndMaterial() throws Exception {
        BatchProductImportRequest request = batch(List.of(
                ProductImportItem.builder()
                        .externalId("FTK-87490974")
                        .name("Костюм летний")
                        .description("Полное описание костюма")
                        .material("Ткань Твил 65% хлопок")
                        .price(new BigDecimal("9267"))
                        .wholesalePrice(new BigDecimal("9267"))
                        .source("FTK")
                        .build()
        ));

        mockMvc.perform(post("/api/v1/products/import/batch")
                        .header("X-Internal-Token", "test-secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(1))
                .andExpect(jsonPath("$.failed").value(0));

        Product saved = productRepository.findAll().stream()
                .filter(p -> "FTK-87490974".equals(p.getExternalId()))
                .findFirst()
                .orElseThrow();

        assertThat(saved.getDescription()).isEqualTo("Полное описание костюма");
        assertThat(saved.getMaterial()).isEqualTo("Ткань Твил 65% хлопок");
        assertThat(saved.getSource()).isEqualTo("FTK");
    }

    @Test
    @WithMockUser
    @DisplayName("POST /import/batch — повторный импорт не затирает description если null")
    void shouldNotOverwriteDescriptionOnReimport() throws Exception {
        // Первый импорт — с description
        BatchProductImportRequest first = batch(List.of(
                ProductImportItem.builder()
                        .externalId("FTK-111")
                        .name("Товар")
                        .description("Описание от ФТК")
                        .material("Хлопок")
                        .price(BigDecimal.ONE)
                        .wholesalePrice(BigDecimal.ONE)
                        .source("FTK")
                        .build()
        ));

        mockMvc.perform(post("/api/v1/products/import/batch")
                        .header("X-Internal-Token", "test-secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(first)))
                .andExpect(status().isOk());

        // Второй импорт — description=null (обновление цены из 1С)
        BatchProductImportRequest second = batch(List.of(
                ProductImportItem.builder()
                        .externalId("FTK-111")
                        .name("Товар")
                        .price(new BigDecimal("2000"))
                        .wholesalePrice(new BigDecimal("2000"))
                        .source("FTK")
                        .build()
        ));

        mockMvc.perform(post("/api/v1/products/import/batch")
                        .header("X-Internal-Token", "test-secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(second)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.updated").value(1));

        Product saved = productRepository.findAll().stream()
                .filter(p -> "FTK-111".equals(p.getExternalId()))
                .findFirst()
                .orElseThrow();

        assertThat(saved.getDescription()).isEqualTo("Описание от ФТК");
        assertThat(saved.getMaterial()).isEqualTo("Хлопок");
        assertThat(saved.getPrice()).isEqualByComparingTo("2000");
    }

    @Test
    @WithMockUser
    @DisplayName("POST /import/batch — shortDescription и description независимы")
    void shouldStoreShortDescriptionAndDescriptionIndependently() throws Exception {
        BatchProductImportRequest request = batch(List.of(
                ProductImportItem.builder()
                        .externalId("FTK-222")
                        .name("Жилет")
                        .description("Полное описание жилета")
                        .shortDescription("Краткое из 1С")
                        .price(new BigDecimal("4660"))
                        .wholesalePrice(new BigDecimal("4660"))
                        .source("FTK")
                        .build()
        ));

        mockMvc.perform(post("/api/v1/products/import/batch")
                        .header("X-Internal-Token", "test-secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        Product saved = productRepository.findAll().stream()
                .filter(p -> "FTK-222".equals(p.getExternalId()))
                .findFirst()
                .orElseThrow();

        assertThat(saved.getDescription()).isEqualTo("Полное описание жилета");
        assertThat(saved.getShortDescription()).isEqualTo("Краткое из 1С");
    }

    @Test
    @WithMockUser
    @DisplayName("POST /import/batch — пустой список возвращает 400 (валидация @NotEmpty)")
    void shouldRejectEmptyBatch() throws Exception {
        BatchProductImportRequest request = batch(List.of());

        mockMvc.perform(post("/api/v1/products/import/batch")
                        .header("X-Internal-Token", "test-secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser
    @DisplayName("POST /import/batch — варианты создаются как дочерние Product записи")
    void shouldImportVariantsAsChildProducts() throws Exception {
        BatchProductImportRequest request = batch(List.of(
                ProductImportItem.builder()
                        .externalId("FTK-PARENT-001")
                        .name("Костюм защитный")
                        .price(new BigDecimal("5000"))
                        .wholesalePrice(new BigDecimal("5000"))
                        .source("FTK")
                        .unitOfMeasure("Пара")
                        .variants(List.of(
                                ProductImportItem.VariantImportItem.builder()
                                        .externalId("FTK-PARENT-001.001")
                                        .sku("KZ-001-S")
                                        .price(new BigDecimal("5000"))
                                        .wholesalePrice(new BigDecimal("5000"))
                                        .stockQuantity(10)
                                        .barcode("4607032891234")
                                        .countryOfOrigin("Россия")
                                        .attributes(Map.of("Размер", "S", "Рост", "164-170"))
                                        .build(),
                                ProductImportItem.VariantImportItem.builder()
                                        .externalId("FTK-PARENT-001.002")
                                        .sku("KZ-001-M")
                                        .price(new BigDecimal("5100"))
                                        .wholesalePrice(new BigDecimal("5100"))
                                        .stockQuantity(15)
                                        .barcode("4607032891235")
                                        .countryOfOrigin("Россия")
                                        .attributes(Map.of("Размер", "M", "Рост", "170-176"))
                                        .build()
                        ))
                        .build()
        ));

        mockMvc.perform(post("/api/v1/products/import/batch")
                        .header("X-Internal-Token", "test-secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(1))
                .andExpect(jsonPath("$.failed").value(0));

        // Родительский товар
        Product parent = productRepository.findAll().stream()
                .filter(p -> "FTK-PARENT-001".equals(p.getExternalId()))
                .findFirst()
                .orElseThrow();
        assertThat(parent.getIsVariantChild()).isFalse();

        // Дочерние товары-варианты как отдельные Product записи
        List<Product> children = productRepository.findAll().stream()
                .filter(p -> p.getParentProductId() != null && p.getParentProductId().equals(parent.getId()))
                .toList();
        assertThat(children).hasSize(2);

        Product childS = children.stream()
                .filter(c -> "FTK-PARENT-001.001".equals(c.getExternalId()))
                .findFirst()
                .orElseThrow();
        assertThat(childS.getIsVariantChild()).isTrue();
        assertThat(childS.getSku()).isEqualTo("KZ-001-S");
        assertThat(childS.getBarcode()).isEqualTo("4607032891234");
        assertThat(childS.getCountryOfOrigin()).isEqualTo("Россия");
        assertThat(childS.getStockQuantity()).isEqualTo(10);
        // единица измерения наследуется от родителя
        assertThat(childS.getUnitOfMeasure()).isEqualTo("Пара");
        // имя варианта содержит размер из атрибутов, а не артикул
        assertThat(childS.getName()).isEqualTo("Костюм защитный (размер S)");
    }

    @Test
    @WithMockUser
    @DisplayName("POST /import/batch — повторный импорт вариантов обновляет дочерние Product")
    void shouldUpdateChildProductsOnReimport() throws Exception {
        // Первый импорт
        BatchProductImportRequest first = batch(List.of(
                ProductImportItem.builder()
                        .externalId("FTK-CHILD-TEST")
                        .name("Куртка зимняя")
                        .price(new BigDecimal("8000"))
                        .wholesalePrice(new BigDecimal("8000"))
                        .source("FTK")
                        .variants(List.of(
                                ProductImportItem.VariantImportItem.builder()
                                        .externalId("FTK-CHILD-TEST.001")
                                        .sku("KZ-L")
                                        .price(new BigDecimal("8000"))
                                        .stockQuantity(5)
                                        .build()
                        ))
                        .build()
        ));
        mockMvc.perform(post("/api/v1/products/import/batch")
                        .header("X-Internal-Token", "test-secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(first)))
                .andExpect(status().isOk());

        // Второй импорт — обновляем цену и остаток варианта
        BatchProductImportRequest second = batch(List.of(
                ProductImportItem.builder()
                        .externalId("FTK-CHILD-TEST")
                        .name("Куртка зимняя")
                        .price(new BigDecimal("8000"))
                        .wholesalePrice(new BigDecimal("8000"))
                        .source("FTK")
                        .variants(List.of(
                                ProductImportItem.VariantImportItem.builder()
                                        .externalId("FTK-CHILD-TEST.001")
                                        .sku("KZ-L")
                                        .price(new BigDecimal("9000"))
                                        .stockQuantity(20)
                                        .build()
                        ))
                        .build()
        ));
        mockMvc.perform(post("/api/v1/products/import/batch")
                        .header("X-Internal-Token", "test-secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(second)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.updated").value(1));

        Product child = productRepository.findAll().stream()
                .filter(p -> "FTK-CHILD-TEST.001".equals(p.getExternalId()))
                .findFirst()
                .orElseThrow();
        assertThat(child.getPrice()).isEqualByComparingTo("9000");
        assertThat(child.getStockQuantity()).isEqualTo(20);
        assertThat(child.getIsVariantChild()).isTrue();
    }

    @Test
    @WithMockUser
    @DisplayName("POST /import/batch — новый товар: CREATED и slug сгенерирован")
    void shouldGenerateSlugAndCreatedForNewProduct() throws Exception {
        BatchProductImportRequest request = batch(List.of(
                ProductImportItem.builder()
                        .externalId("FTK-NEW-001")
                        .name("Новый костюм")
                        .price(new BigDecimal("1000"))
                        .wholesalePrice(new BigDecimal("1000"))
                        .source("FTK")
                        .build()
        ));

        mockMvc.perform(post("/api/v1/products/import/batch")
                        .header("X-Internal-Token", "test-secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(1))
                .andExpect(jsonPath("$.updated").value(0))
                .andExpect(jsonPath("$.unchanged").value(0));

        Product saved = productRepository.findAll().stream()
                .filter(p -> "FTK-NEW-001".equals(p.getExternalId()))
                .findFirst()
                .orElseThrow();
        assertThat(saved.getSlug()).isEqualTo("novyy-kostyum");
    }

    @Test
    @WithMockUser
    @DisplayName("POST /import/batch — повторный импорт без изменений: slug не меняется, счётчик unchanged растёт")
    void shouldKeepSlugAndCountAsUnchangedWhenNothingChanged() throws Exception {
        ProductImportItem item = ProductImportItem.builder()
                .externalId("FTK-SAME-001")
                .name("Товар без изменений")
                .price(new BigDecimal("1500"))
                .wholesalePrice(new BigDecimal("1500"))
                .source("FTK")
                .build();
        BatchProductImportRequest request = batch(List.of(item));

        mockMvc.perform(post("/api/v1/products/import/batch")
                        .header("X-Internal-Token", "test-secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(1));

        Product afterFirst = productRepository.findAll().stream()
                .filter(p -> "FTK-SAME-001".equals(p.getExternalId()))
                .findFirst()
                .orElseThrow();
        String slugAfterFirst = afterFirst.getSlug();

        // Повторный импорт — абсолютно те же данные
        mockMvc.perform(post("/api/v1/products/import/batch")
                        .header("X-Internal-Token", "test-secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(0))
                .andExpect(jsonPath("$.updated").value(0))
                .andExpect(jsonPath("$.unchanged").value(1));

        Product afterSecond = productRepository.findAll().stream()
                .filter(p -> "FTK-SAME-001".equals(p.getExternalId()))
                .findFirst()
                .orElseThrow();
        assertThat(afterSecond.getSlug()).isEqualTo(slugAfterFirst);
    }

    @Test
    @WithMockUser
    @DisplayName("POST /import/batch — повторный импорт с изменённой ценой: UPDATED, slug не меняется")
    void shouldCountAsUpdatedAndKeepSlugWhenFieldChanged() throws Exception {
        ProductImportItem first = ProductImportItem.builder()
                .externalId("FTK-CHANGED-001")
                .name("Товар с изменением цены")
                .price(new BigDecimal("1000"))
                .wholesalePrice(new BigDecimal("1000"))
                .source("FTK")
                .build();

        mockMvc.perform(post("/api/v1/products/import/batch")
                        .header("X-Internal-Token", "test-secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(batch(List.of(first)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(1));

        Product afterFirst = productRepository.findAll().stream()
                .filter(p -> "FTK-CHANGED-001".equals(p.getExternalId()))
                .findFirst()
                .orElseThrow();
        String slugAfterFirst = afterFirst.getSlug();

        ProductImportItem second = ProductImportItem.builder()
                .externalId("FTK-CHANGED-001")
                .name("Товар с изменением цены")
                .price(new BigDecimal("1200"))
                .wholesalePrice(new BigDecimal("1000"))
                .source("FTK")
                .build();

        mockMvc.perform(post("/api/v1/products/import/batch")
                        .header("X-Internal-Token", "test-secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(batch(List.of(second)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(0))
                .andExpect(jsonPath("$.updated").value(1))
                .andExpect(jsonPath("$.unchanged").value(0));

        Product afterSecond = productRepository.findAll().stream()
                .filter(p -> "FTK-CHANGED-001".equals(p.getExternalId()))
                .findFirst()
                .orElseThrow();
        assertThat(afterSecond.getSlug()).isEqualTo(slugAfterFirst);
        assertThat(afterSecond.getPrice()).isEqualByComparingTo("1200");
    }

    @Test
    @WithMockUser
    @DisplayName("POST /import/batch — импорт barcode и countryOfOrigin на уровне товара")
    void shouldImportBarcodeAndCountryOfOrigin() throws Exception {
        BatchProductImportRequest request = batch(List.of(
                ProductImportItem.builder()
                        .externalId("FTK-BARCODE-001")
                        .name("Перчатки рабочие")
                        .price(new BigDecimal("350"))
                        .wholesalePrice(new BigDecimal("350"))
                        .source("FTK")
                        .barcode("4600000000001")
                        .countryOfOrigin("Китай")
                        .build()
        ));

        mockMvc.perform(post("/api/v1/products/import/batch")
                        .header("X-Internal-Token", "test-secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(1));

        Product saved = productRepository.findAll().stream()
                .filter(p -> "FTK-BARCODE-001".equals(p.getExternalId()))
                .findFirst()
                .orElseThrow();
        assertThat(saved.getBarcode()).isEqualTo("4600000000001");
        assertThat(saved.getCountryOfOrigin()).isEqualTo("Китай");
    }

    @Test
    @WithMockUser
    @DisplayName("POST /import/batch — пачка без warehouseCode отклоняется 400")
    void shouldRejectBatchWithoutWarehouseCode() throws Exception {
        BatchProductImportRequest request = new BatchProductImportRequest(List.of(
                ProductImportItem.builder().externalId("1C-NOWH").name("Товар").build()), null);

        mockMvc.perform(post("/api/v1/products/import/batch")
                        .header("X-Internal-Token", "test-secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser
    @DisplayName("POST /import/batch — неизвестный склад отклоняется 400")
    void shouldRejectUnknownWarehouse() throws Exception {
        BatchProductImportRequest request = new BatchProductImportRequest(List.of(
                ProductImportItem.builder().externalId("1C-UNK").name("Товар").build()), "NOWHERE");

        mockMvc.perform(post("/api/v1/products/import/batch")
                        .header("X-Internal-Token", "test-secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser
    @DisplayName("POST /import/batch — остаток товара 1С пишется в product_stocks и в stock_quantity")
    void shouldWriteStockToWarehouse() throws Exception {
        BatchProductImportRequest request = batch(List.of(
                ProductImportItem.builder().externalId("1C-STOCK").name("Перчатки 1С").stockQuantity(50).build()));

        mockMvc.perform(post("/api/v1/products/import/batch")
                        .header("X-Internal-Token", "test-secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.failed").value(0));

        Product saved = productRepository.findByExternalId("1C-STOCK").orElseThrow();
        assertThat(saved.getStockQuantity()).isEqualTo(50);
        assertThat(productStockRepository.findByProductIdAndWarehouseId(saved.getId(), warehouse.getId()))
                .get().extracting(s -> s.getQuantity()).isEqualTo(50);
    }

    @Test
    @WithMockUser
    @DisplayName("POST /import/batch — товар 1С с тем же sku склеивается с товаром Факела, остаток суммируется")
    void shouldAutoMergeOneCIntoFtkBySku() throws Exception {
        Product ftk = productRepository.save(Product.builder()
                .name("Перчатки Гвард").slug("perchatki-gvard").sku("5004").source("FTK")
                .externalId("FTK-5004").stockQuantity(0).isActive(true).build());
        productStockRepository.save(ru.rfsnab.productservice.model.ProductStock.builder()
                .product(ftk).warehouse(warehouse).quantity(230).build());

        BatchProductImportRequest request = batch(List.of(
                ProductImportItem.builder().externalId("1C-5004").name("Перчатки 1С").sku("5004").stockQuantity(50).build()));

        mockMvc.perform(post("/api/v1/products/import/batch")
                        .header("X-Internal-Token", "test-secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.failed").value(0));

        Product oneC = productRepository.findByExternalId("1C-5004").orElseThrow();
        assertThat(oneC.getMergedIntoProductId()).isEqualTo(ftk.getId());
        assertThat(oneC.getIsActive()).isFalse();
        assertThat(productRepository.findById(ftk.getId()).orElseThrow().getStockQuantity()).isEqualTo(280);
    }
}
