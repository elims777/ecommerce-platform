package ru.rfsnab.productservice.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import ru.rfsnab.productservice.dto.BatchProductImportRequest;
import ru.rfsnab.productservice.dto.BatchProductImportResponse;
import ru.rfsnab.productservice.dto.BatchProductImportResponse.ImportAction;
import ru.rfsnab.productservice.dto.ProductImportItem;
import ru.rfsnab.productservice.model.Category;
import ru.rfsnab.productservice.exception.BusinessException;
import ru.rfsnab.productservice.exception.WarehouseNotFoundException;
import ru.rfsnab.productservice.model.Product;
import ru.rfsnab.productservice.model.Warehouse;
import ru.rfsnab.productservice.repository.CategoryRepository;
import ru.rfsnab.productservice.repository.ProductRepository;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProductImportService Unit Tests")
class ProductImportServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private PlatformTransactionManager transactionManager;

    @Mock
    private SlugGeneratorService slugService;

    @Mock
    private ProductStockService stockService;

    @Mock
    private WarehouseService warehouseService;

    @Mock
    private ProductMergeService mergeService;

    private static final String WAREHOUSE_CODE = "PUSHKIN";

    private ProductImportService importService;

    private Warehouse pushkin;

    private Category importCategory;

    @BeforeEach
    void setUp() {
        // Импорт выполняет каждый товар в своей транзакции через TransactionTemplate:
        // без этого стаба мок отдаёт null вместо TransactionStatus и товары не обрабатываются.
        lenient().when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());

        importService = new ProductImportService(productRepository, categoryRepository, transactionManager, slugService,
                stockService, warehouseService, mergeService);
        ReflectionTestUtils.setField(importService, "chunkSize", 25);

        importCategory = Category.builder().id(1L).name("Импорт из 1С").slug("import-1c").build();
        lenient().when(categoryRepository.findBySlug("import-1c")).thenReturn(Optional.of(importCategory));
        lenient().when(productRepository.findAllSlugs()).thenReturn(List.of());
        pushkin = Warehouse.builder().id(2L).code(WAREHOUSE_CODE).name("Склад Пушкин").city("Пушкин").build();
        when(warehouseService.requireActiveByCode(WAREHOUSE_CODE)).thenReturn(pushkin);
    }

    // ==================== upsertChildVariants() — категория ребёнка ====================

    @Nested
    @DisplayName("upsertChildVariants() — синхронизация категории")
    class ChildCategorySyncTests {

        @Test
        @DisplayName("при повторном импорте категория существующего варианта обновляется вслед за родителем")
        void reimport_ParentCategoryChanged_UpdatesExistingChildCategory() {
            // Given: родитель уже существует и привязан к новой категории
            Category oldCategory = Category.builder().id(2L).name("Старая категория").slug("old-cat").build();
            Category newCategory = Category.builder().id(3L).name("Новая категория").slug("new-cat").build();

            Product parent = Product.builder()
                    .id(10L)
                    .externalId("PARENT-1")
                    .name("Куртка")
                    .category(newCategory)
                    .source("INTERNAL")
                    .unitOfMeasure("шт")
                    .build();

            Product existingChild = Product.builder()
                    .id(11L)
                    .externalId("PARENT-1.001")
                    .name("Куртка (размер L)")
                    .category(oldCategory)
                    .isVariantChild(true)
                    .parentProductId(10L)
                    .unitOfMeasure("шт")
                    .source("INTERNAL")
                    .build();

            ProductImportItem.VariantImportItem variantItem = ProductImportItem.VariantImportItem.builder()
                    .externalId("PARENT-1.001")
                    .attributes(java.util.Map.of("Размер", "L"))
                    .build();

            ProductImportItem item = ProductImportItem.builder()
                    .externalId("PARENT-1")
                    .name("Куртка")
                    .variants(List.of(variantItem))
                    .build();

            when(productRepository.findByExternalIdIn(any())).thenAnswer(inv -> {
                List<String> ids = inv.getArgument(0);
                if (ids.contains("PARENT-1") && !ids.contains("PARENT-1.001")) {
                    return List.of(parent);
                }
                if (ids.contains("PARENT-1.001")) {
                    return List.of(existingChild);
                }
                return List.of();
            });
            when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

            BatchProductImportRequest request = BatchProductImportRequest.builder()
                    .items(List.of(item))
                    .warehouseCode(WAREHOUSE_CODE)
                    .build();

            // When
            BatchProductImportResponse response = importService.importBatch(request);

            // Then — категория ребёнка обновилась на категорию родителя
            assertThat(existingChild.getCategory()).isEqualTo(newCategory);
            assertThat(response.getResults()).hasSize(1);
            assertThat(response.getResults().get(0).getAction()).isEqualTo(ImportAction.UPDATED);
        }

        @Test
        @DisplayName("при создании нового варианта категория берётся у родителя")
        void firstImport_NewChild_TakesParentCategory() {
            // Given: родитель уже существует, вариант ещё не существует
            Category parentCategory = Category.builder().id(4L).name("Категория").slug("cat").build();

            Product parent = Product.builder()
                    .id(20L)
                    .externalId("PARENT-2")
                    .name("Ботинки")
                    .category(parentCategory)
                    .source("INTERNAL")
                    .unitOfMeasure("пара")
                    .build();

            ProductImportItem.VariantImportItem variantItem = ProductImportItem.VariantImportItem.builder()
                    .externalId("PARENT-2.001")
                    .attributes(java.util.Map.of("Размер", "42"))
                    .build();

            ProductImportItem item = ProductImportItem.builder()
                    .externalId("PARENT-2")
                    .name("Ботинки")
                    .variants(List.of(variantItem))
                    .build();

            when(productRepository.findByExternalIdIn(any())).thenAnswer(inv -> {
                List<String> ids = inv.getArgument(0);
                if (ids.contains("PARENT-2") && !ids.contains("PARENT-2.001")) {
                    return List.of(parent);
                }
                return List.of();
            });
            when(slugService.generateUniqueSlug(anyString(), any())).thenReturn("botinki-42");
            when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

            BatchProductImportRequest request = BatchProductImportRequest.builder()
                    .items(List.of(item))
                    .warehouseCode(WAREHOUSE_CODE)
                    .build();

            // When
            BatchProductImportResponse response = importService.importBatch(request);

            // Then — родитель уже существовал, значит action=UPDATED (создание отражено в childrenChanged)
            assertThat(response.getUpdated()).isEqualTo(1);
            assertThat(response.getResults().get(0).getAction()).isEqualTo(ImportAction.UPDATED);
        }
    }

    // ==================== импорт не применяет акционную наценку ====================

    @Nested
    @DisplayName("цены импорта сохраняются как есть (акции считаются при выдаче)")
    class ImportKeepsBasePricesTests {

        /**
         * Регрессия: раньше импорт домножал цену ФТК-товаров «Распродажи» на 1.10 и писал
         * результат в БД. Из-за захардкоженного id наценка попадала на чужую категорию,
         * а откатить её было нечем. Теперь проценты применяются только при выдаче.
         */
        @Test
        @DisplayName("товар ФТК в акционной категории — в БД пишется базовая цена без наценки")
        void ftkProductInSaleCategory_BasePriceStored() {
            Category saleSubCategory = Category.builder().id(54L).name("Спецобувь (распродажа)").slug("spetsobuv-rasprodazha").build();

            ProductImportItem item = ProductImportItem.builder()
                    .externalId("FTK-1")
                    .name("Ботинки ФТК")
                    .source("FTK")
                    .categoryId(54L)
                    .price(new java.math.BigDecimal("100.00"))
                    .wholesalePrice(new java.math.BigDecimal("120.00"))
                    .build();

            when(categoryRepository.findById(54L)).thenReturn(Optional.of(saleSubCategory));
            when(slugService.generateUniqueSlug(anyString(), any())).thenReturn("botinki-ftk");
            when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

            BatchProductImportResponse response = importService.importBatch(
                    BatchProductImportRequest.builder().items(List.of(item)).warehouseCode(WAREHOUSE_CODE).build());

            assertThat(response.getResults().get(0).isSuccess()).isTrue();
            org.mockito.ArgumentCaptor<Product> captor = org.mockito.ArgumentCaptor.forClass(Product.class);
            verify(productRepository, org.mockito.Mockito.atLeastOnce()).save(captor.capture());
            Product saved = captor.getValue();
            assertThat(saved.getPrice()).isEqualByComparingTo("100.00");
            assertThat(saved.getWholesalePrice()).isEqualByComparingTo("120.00");
        }

        @Test
        @DisplayName("повторный импорт не накручивает процент поверх предыдущего")
        void repeatedImport_PriceNotCompounded() {
            Category saleSubCategory = Category.builder().id(54L).name("Спецобувь (распродажа)").slug("spetsobuv-rasprodazha").build();
            Product existing = Product.builder()
                    .id(7L)
                    .externalId("FTK-1")
                    .name("Ботинки ФТК")
                    .source("FTK")
                    .category(saleSubCategory)
                    .price(new java.math.BigDecimal("100.00"))
                    .build();

            ProductImportItem item = ProductImportItem.builder()
                    .externalId("FTK-1")
                    .name("Ботинки ФТК")
                    .source("FTK")
                    .categoryId(54L)
                    .price(new java.math.BigDecimal("100.00"))
                    .build();

            when(productRepository.findByExternalIdIn(List.of("FTK-1"))).thenReturn(List.of(existing));
            when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

            importService.importBatch(BatchProductImportRequest.builder().items(List.of(item)).warehouseCode(WAREHOUSE_CODE).build());

            org.mockito.ArgumentCaptor<Product> captor = org.mockito.ArgumentCaptor.forClass(Product.class);
            verify(productRepository, org.mockito.Mockito.atLeastOnce()).save(captor.capture());
            assertThat(captor.getValue().getPrice()).isEqualByComparingTo("100.00");
        }
    }

    // ==================== processItem() — properties родителя → attributes ====================

    @Test
    @DisplayName("свойства (properties) родителя сохраняются как ProductAttribute")
    void shouldPersistParentPropertiesAsAttributes() {
        ProductImportItem item = ProductImportItem.builder()
                .externalId("FTK-100")
                .name("Костюм летний")
                .source("FTK")
                .properties(new LinkedHashMap<>(Map.of(
                        "Состав ткани", "100% хлопок",
                        "Защитные свойства", "Ми")))
                .build();

        when(slugService.generateUniqueSlug(anyString(), any())).thenReturn("kostyum-letniy");
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        BatchProductImportResponse response = importService.importBatch(
                BatchProductImportRequest.builder().items(List.of(item)).warehouseCode(WAREHOUSE_CODE).build());

        assertThat(response.getResults().get(0).isSuccess()).isTrue();
        org.mockito.ArgumentCaptor<Product> captor = org.mockito.ArgumentCaptor.forClass(Product.class);
        verify(productRepository, org.mockito.Mockito.atLeastOnce()).save(captor.capture());
        Product saved = captor.getAllValues().stream()
                .filter(p -> "FTK-100".equals(p.getExternalId()))
                .findFirst().orElseThrow();

        assertThat(saved.getAttributes())
                .extracting(a -> a.getAttributeName() + "=" + a.getAttributeValue())
                .containsExactlyInAnyOrder("Состав ткани=100% хлопок", "Защитные свойства=Ми");
    }

    // ==================== остатки по складам и автосклейка ====================

    @Nested
    @DisplayName("остатки пишутся в склад, склейка запускается из импорта")
    class StockAndMergeTests {

        private BatchProductImportResponse run(ProductImportItem item) {
            when(slugService.generateUniqueSlug(anyString(), any())).thenReturn("slug-" + item.getExternalId());
            when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));
            return importService.importBatch(
                    BatchProductImportRequest.builder().items(List.of(item)).warehouseCode(WAREHOUSE_CODE).build());
        }

        @Test
        @DisplayName("stockQuantity товара уходит в ProductStockService, а не в поле товара напрямую")
        void stockQuantity_GoesToWarehouse() {
            ProductImportItem item = ProductImportItem.builder()
                    .externalId("FTK-1").name("Ботинки").source("FTK").stockQuantity(7).build();

            run(item);

            org.mockito.ArgumentCaptor<Product> captor = org.mockito.ArgumentCaptor.forClass(Product.class);
            verify(stockService).setQuantity(captor.capture(), org.mockito.ArgumentMatchers.eq(pushkin),
                    org.mockito.ArgumentMatchers.eq(7));
            assertThat(captor.getValue().getExternalId()).isEqualTo("FTK-1");
            assertThat(captor.getValue().getStockQuantity()).isZero();
        }

        @Test
        @DisplayName("stockQuantity=null (нет offer) — остаток не трогается")
        void nullStock_NoSetQuantity() {
            run(ProductImportItem.builder().externalId("1C-1").name("Перчатки").build());

            verify(stockService, never()).setQuantity(any(), any(), org.mockito.ArgumentMatchers.anyInt());
        }

        @Test
        @DisplayName("остаток варианта Факела пишется в склад")
        void variantStock_GoesToWarehouse() {
            Product parent = Product.builder().id(10L).externalId("FTK-P").name("Куртка").source("FTK")
                    .category(importCategory).build();
            when(productRepository.findByExternalIdIn(any())).thenAnswer(inv -> {
                List<String> ids = inv.getArgument(0);
                return ids.contains("FTK-P") && !ids.contains("FTK-P.001") ? List.of(parent) : List.of();
            });
            ProductImportItem item = ProductImportItem.builder()
                    .externalId("FTK-P").name("Куртка").source("FTK")
                    .variants(List.of(ProductImportItem.VariantImportItem.builder()
                            .externalId("FTK-P.001").sku("K-1").stockQuantity(3).build()))
                    .build();

            run(item);

            org.mockito.ArgumentCaptor<Product> captor = org.mockito.ArgumentCaptor.forClass(Product.class);
            verify(stockService).setQuantity(captor.capture(), org.mockito.ArgumentMatchers.eq(pushkin),
                    org.mockito.ArgumentMatchers.eq(3));
            assertThat(captor.getValue().getExternalId()).isEqualTo("FTK-P.001");
        }

        @Test
        @DisplayName("товар 1С — автосклейка 1С")
        void oneCProduct_AutoMergeOneC() {
            run(ProductImportItem.builder().externalId("uuid-1").name("Перчатки").sku("5004").build());

            verify(mergeService).autoMergeOneC(any(Product.class));
            verify(mergeService, never()).autoMergeFtk(any());
        }

        @Test
        @DisplayName("товар Факела без вариантов — автосклейка Факела")
        void ftkWithoutVariants_AutoMergeFtk() {
            run(ProductImportItem.builder().externalId("FTK-2").name("Перчатки").source("FTK").sku("5004").build());

            verify(mergeService).autoMergeFtk(any(Product.class));
            verify(mergeService, never()).autoMergeOneC(any());
        }

        @Test
        @DisplayName("товар Факела с вариантами — автосклейка для каждого варианта, но не для родителя")
        void ftkWithVariants_AutoMergeOnlyVariants() {
            ProductImportItem item = ProductImportItem.builder()
                    .externalId("FTK-3").name("Куртка").source("FTK")
                    .variants(List.of(
                            ProductImportItem.VariantImportItem.builder().externalId("FTK-3.001").sku("K-1").build(),
                            ProductImportItem.VariantImportItem.builder().externalId("FTK-3.002").sku("K-2").build()))
                    .build();

            run(item);

            org.mockito.ArgumentCaptor<Product> captor = org.mockito.ArgumentCaptor.forClass(Product.class);
            verify(mergeService, org.mockito.Mockito.times(2)).autoMergeFtk(captor.capture());
            assertThat(captor.getAllValues()).extracting(Product::getExternalId)
                    .containsExactly("FTK-3.001", "FTK-3.002");
        }

        @Test
        @DisplayName("выключенный склад — исключение пробрасывается, пачка не обрабатывается")
        void inactiveWarehouse_Propagates() {
            when(warehouseService.requireActiveByCode(WAREHOUSE_CODE))
                    .thenThrow(new BusinessException("Склад PUSHKIN выключен"));

            assertThatThrownBy(() -> importService.importBatch(BatchProductImportRequest.builder()
                    .items(List.of(ProductImportItem.builder().externalId("1C-1").name("X").build()))
                    .warehouseCode(WAREHOUSE_CODE).build()))
                    .isInstanceOf(BusinessException.class);
            verify(productRepository, never()).save(any());
        }

        @Test
        @DisplayName("неизвестный склад превращается в BusinessException (400, а не 404)")
        void unknownWarehouse_BecomesBusinessException() {
            when(warehouseService.requireActiveByCode(WAREHOUSE_CODE))
                    .thenThrow(new WarehouseNotFoundException("Склад PUSHKIN не найден"));

            assertThatThrownBy(() -> importService.importBatch(BatchProductImportRequest.builder()
                    .items(List.of(ProductImportItem.builder().externalId("1C-1").name("X").build()))
                    .warehouseCode(WAREHOUSE_CODE).build()))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage("Склад PUSHKIN не найден");
        }
    }
}
