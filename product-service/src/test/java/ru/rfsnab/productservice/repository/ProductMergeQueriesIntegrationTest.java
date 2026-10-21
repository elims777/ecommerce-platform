package ru.rfsnab.productservice.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import ru.rfsnab.productservice.BaseIntegrationTest;
import ru.rfsnab.productservice.model.Category;
import ru.rfsnab.productservice.model.Product;
import ru.rfsnab.productservice.spec.ProductSpecifications;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Запросы склейки товаров Integration")
class ProductMergeQueriesIntegrationTest extends BaseIntegrationTest {

    private static final String SKU = "5004";
    private static final String ONE_C_NAME = "Перчатки Гвард 1С";

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    private Category category;

    @BeforeEach
    void setUp() {
        productRepository.deleteAll();
        categoryRepository.deleteAll();
        category = categoryRepository.save(Category.builder()
                .name("Перчатки")
                .slug("perchatki-" + System.nanoTime())
                .isActive(true)
                .displayOrder(0)
                .build());
    }

    private Product save(String name, String source, String externalId, Long parentId, Long mergedInto,
                         boolean mergeLocked, boolean active) {
        return productRepository.save(Product.builder()
                .name(name)
                .slug(name.toLowerCase().replace(" ", "-") + "-" + System.nanoTime())
                .category(category)
                .sku(SKU)
                .source(source)
                .externalId(externalId)
                .parentProductId(parentId)
                .isVariantChild(parentId != null)
                .mergedIntoProductId(mergedInto)
                .mergeLocked(mergeLocked)
                .isActive(active)
                .stockQuantity(10)
                .build());
    }

    @Test
    @DisplayName("findMergeableFtkBySku не возвращает родителя с вариантами, но возвращает вариант и простой товар")
    void findMergeableFtkBySku_SkipsParentWithVariants() {
        Product parent = save("Костюм", "FTK", "FTK-1", null, null, false, true);
        Product variant = save("Костюм 48", "FTK", "FTK-1.001", parent.getId(), null, false, true);
        Product simple = save("Перчатки Факел", "FTK", "FTK-2", null, null, false, true);

        List<Product> result = productRepository.findMergeableFtkBySku(SKU);

        assertThat(result).extracting(Product::getId)
                .containsExactlyInAnyOrder(variant.getId(), simple.getId());
    }

    @Test
    @DisplayName("findMergeableOneCBySku не возвращает склеенный, заблокированный и ручной (без externalId) товары")
    void findMergeableOneCBySku_SkipsMergedLockedAndManual() {
        Product main = save("Перчатки Факел", "FTK", "FTK-2", null, null, false, true);
        Product free = save("1С свободный", "INTERNAL", "uuid-1", null, null, false, false);
        save("1С склеенный", "INTERNAL", "uuid-2", null, main.getId(), false, false);
        save("1С заблокированный", "INTERNAL", "uuid-3", null, null, true, false);
        save("Ручной", "INTERNAL", null, null, null, false, true);

        List<Product> result = productRepository.findMergeableOneCBySku(SKU);

        assertThat(result).extracting(Product::getId).containsExactly(free.getId());
    }

    @Test
    @DisplayName("склеенный (неактивный) товар 1С не попадает в поиск и фильтр категории")
    void mergedProduct_HiddenFromSearchAndCategory() {
        Product main = save("Перчатки Факел", "FTK", "FTK-2", null, null, false, true);
        save(ONE_C_NAME, "INTERNAL", "uuid-1", null, main.getId(), false, false);

        List<Product> search = productRepository.searchByNameOrArticle("гвард", PageRequest.of(0, 10)).getContent();
        List<Product> byCategory = productRepository.findAll(
                ProductSpecifications.categoryWithAttributes(List.of(category.getId()), Map.of(), null),
                PageRequest.of(0, 10)).getContent();

        assertThat(search).extracting(Product::getName).doesNotContain(ONE_C_NAME);
        assertThat(byCategory).extracting(Product::getName).containsExactly("Перчатки Факел");
    }

    @Test
    @DisplayName("findUnmatchedOneC и findMergeCandidates возвращают только несклеенные товары 1С")
    void unmatchedAndCandidates_OnlyUnmergedOneC() {
        Product main = save("Перчатки Факел", "FTK", "FTK-2", null, null, false, true);
        Product free = save(ONE_C_NAME, "INTERNAL", "uuid-1", null, null, false, false);
        save("1С склеенный", "INTERNAL", "uuid-2", null, main.getId(), false, false);

        assertThat(productRepository.findUnmatchedOneC(PageRequest.of(0, 10)).getContent())
                .extracting(Product::getId).containsExactly(free.getId());
        assertThat(productRepository.findMergeCandidates("гвард", PageRequest.of(0, 10)).getContent())
                .extracting(Product::getId).containsExactly(free.getId());
    }

    @Test
    @DisplayName("findByMergedIntoProductIdInOrderByIdAsc возвращает склеенные записи по id")
    void findByMergedInto_ReturnsMergedInIdOrder() {
        Product main = save("Перчатки Факел", "FTK", "FTK-2", null, null, false, true);
        Product first = save("1С первый", "INTERNAL", "uuid-1", null, main.getId(), false, false);
        Product second = save("1С второй", "INTERNAL", "uuid-2", null, main.getId(), false, false);

        assertThat(productRepository.findByMergedIntoProductIdInOrderByIdAsc(List.of(main.getId())))
                .extracting(Product::getId).containsExactly(first.getId(), second.getId());
    }
}
