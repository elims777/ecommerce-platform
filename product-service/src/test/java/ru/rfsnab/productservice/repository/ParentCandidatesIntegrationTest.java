package ru.rfsnab.productservice.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import ru.rfsnab.productservice.BaseIntegrationTest;
import ru.rfsnab.productservice.model.Category;
import ru.rfsnab.productservice.model.Product;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Поиск кандидатов в родители для админки: в отличие от публичного поиска
 * не отсекает неактивные товары (свежий импорт неактивен).
 */
@DisplayName("Поиск кандидатов в родители Integration")
class ParentCandidatesIntegrationTest extends BaseIntegrationTest {

    private static final List<Long> NO_EXCLUDE = List.of(-1L);

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    private Category category;
    private Category otherCategory;

    @BeforeEach
    void setUp() {
        productRepository.deleteAll();
        categoryRepository.deleteAll();
        category = saveCategory("Каски");
        otherCategory = saveCategory("Перчатки");
    }

    private Category saveCategory(String name) {
        return categoryRepository.save(Category.builder()
                .name(name)
                .slug(name.toLowerCase() + "-" + System.nanoTime())
                .isActive(true)
                .displayOrder(0)
                .build());
    }

    private Product save(String name, String sku, Category cat, boolean isActive, boolean isVariantChild) {
        return productRepository.save(Product.builder()
                .name(name)
                .slug(name.toLowerCase().replace(" ", "-") + "-" + System.nanoTime())
                .sku(sku)
                .category(cat)
                .isActive(isActive)
                .isVariantChild(isVariantChild)
                .stockQuantity(0)
                .build());
    }

    private List<Long> search(String query, Long categoryId, List<Long> excludeIds) {
        return productRepository.findParentCandidates(query, categoryId, excludeIds, PageRequest.of(0, 20))
                .getContent().stream().map(Product::getId).toList();
    }

    @Test
    @DisplayName("неактивный товар находится")
    void shouldFindInactiveProduct() {
        Product inactive = save("Каска строительная", "K-1", category, false, false);

        assertThat(search("каска", null, NO_EXCLUDE)).containsExactly(inactive.getId());
    }

    @Test
    @DisplayName("поиск по артикулу находит товар")
    void shouldFindBySku() {
        Product product = save("Перчатки рабочие", "ART-777", category, false, false);

        assertThat(search("art-777", null, NO_EXCLUDE)).containsExactly(product.getId());
    }

    @Test
    @DisplayName("товар из categoryId идёт первым, даже если по алфавиту позже")
    void shouldPutRequestedCategoryFirst() {
        Product other = save("Аврора", "A-1", otherCategory, true, false);
        Product same = save("Яблоко", "Y-1", category, true, false);

        assertThat(search("", category.getId(), NO_EXCLUDE)).containsExactly(same.getId(), other.getId());
    }

    @Test
    @DisplayName("в одной категории «начинается с запроса» выше «содержит»")
    void shouldRankPrefixAboveContains() {
        Product contains = save("Большая каска", "B-1", category, true, false);
        Product prefix = save("Каска малая", "Z-1", category, true, false);
        Product skuPrefix = save("Шлем", "каска-1", category, true, false);

        assertThat(search("каска", category.getId(), NO_EXCLUDE))
                .containsExactly(prefix.getId(), skuPrefix.getId(), contains.getId());
    }

    @Test
    @DisplayName("варианты и excludeIds в выдачу не попадают")
    void shouldExcludeVariantsAndExcludedIds() {
        Product parent = save("Куртка", "J-1", category, true, false);
        Product excluded = save("Куртка зимняя", "J-2", category, true, false);
        save("Куртка 46", "J-3", category, true, true);

        assertThat(search("куртка", null, List.of(excluded.getId()))).containsExactly(parent.getId());
    }

    @Test
    @DisplayName("товар без категории возвращается (LEFT JOIN)")
    void shouldReturnProductWithoutCategory() {
        Product noCategory = save("Куртка", "J-1", null, false, false);

        assertThat(search("куртка", category.getId(), NO_EXCLUDE)).containsExactly(noCategory.getId());
    }
}
