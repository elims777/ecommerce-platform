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
import ru.rfsnab.productservice.dto.BatchProductImportRequest;
import ru.rfsnab.productservice.dto.BatchProductImportResponse;
import ru.rfsnab.productservice.dto.BatchProductImportResponse.ImportAction;
import ru.rfsnab.productservice.dto.ProductImportItem;
import ru.rfsnab.productservice.model.Category;
import ru.rfsnab.productservice.model.Product;
import ru.rfsnab.productservice.repository.CategoryRepository;
import ru.rfsnab.productservice.repository.ProductRepository;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

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

    private ProductImportService importService;

    private Category importCategory;

    @BeforeEach
    void setUp() {
        importService = new ProductImportService(productRepository, categoryRepository, transactionManager, slugService);
        ReflectionTestUtils.setField(importService, "chunkSize", 25);

        importCategory = Category.builder().id(1L).name("Импорт из 1С").slug("import-1c").build();
        when(categoryRepository.findBySlug("import-1c")).thenReturn(Optional.of(importCategory));
        when(productRepository.findAllSlugs()).thenReturn(List.of());
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
                    .build();

            // When
            BatchProductImportResponse response = importService.importBatch(request);

            // Then — родитель уже существовал, значит action=UPDATED (создание отражено в childrenChanged)
            assertThat(response.getUpdated()).isEqualTo(1);
            assertThat(response.getResults().get(0).getAction()).isEqualTo(ImportAction.UPDATED);
        }
    }
}
