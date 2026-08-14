package ru.rfsnab.productservice.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import ru.rfsnab.productservice.BaseIntegrationTest;
import ru.rfsnab.productservice.model.Category;
import ru.rfsnab.productservice.model.Product;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Админский фильтр по активности работает по семье: неактивный вариант
 * под активным родителем не должен «прятаться» ни под одним фильтром.
 */
@DisplayName("Админский фильтр активности по семье Integration")
class AdminFamilyFilterIntegrationTest extends BaseIntegrationTest {

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
                .name("Одежда")
                .slug("odezhda-" + System.nanoTime())
                .isActive(true)
                .displayOrder(0)
                .build());
    }

    private Product saveParent(String name, boolean isActive) {
        return productRepository.save(Product.builder()
                .name(name)
                .slug(name.toLowerCase().replace(" ", "-") + "-" + System.nanoTime())
                .category(category)
                .isActive(isActive)
                .isVariantChild(false)
                .stockQuantity(0)
                .build());
    }

    private void saveChild(String name, boolean isActive, Long parentId) {
        productRepository.save(Product.builder()
                .name(name)
                .slug(name.toLowerCase().replace(" ", "-") + "-" + System.nanoTime())
                .category(category)
                .isActive(isActive)
                .isVariantChild(true)
                .parentProductId(parentId)
                .stockQuantity(0)
                .build());
    }

    @Test
    @DisplayName("активный родитель с неактивным вариантом попадает в фильтр «Неактивные»")
    void shouldShowActiveParentWithInactiveVariant() {
        Product parent = saveParent("Куртка", true);
        saveChild("Куртка 44", true, parent.getId());
        saveChild("Куртка 46", false, parent.getId());

        Page<Product> inactive = productRepository.findFamiliesByActive(false, PageRequest.of(0, 20));

        assertThat(inactive.getContent())
                .as("родителя не должно быть видно только потому, что он сам активен")
                .extracting(Product::getId)
                .containsExactly(parent.getId());
    }

    @Test
    @DisplayName("та же семья остаётся и в фильтре «Активные»")
    void shouldKeepMixedFamilyInActiveFilter() {
        Product parent = saveParent("Куртка", true);
        saveChild("Куртка 46", false, parent.getId());

        Page<Product> active = productRepository.findFamiliesByActive(true, PageRequest.of(0, 20));

        assertThat(active.getContent()).extracting(Product::getId).containsExactly(parent.getId());
    }

    @Test
    @DisplayName("варианты сами по себе в выдачу не попадают")
    void shouldNeverReturnVariantsAsRows() {
        Product parent = saveParent("Куртка", false);
        saveChild("Куртка 46", false, parent.getId());

        Page<Product> inactive = productRepository.findFamiliesByActive(false, PageRequest.of(0, 20));

        assertThat(inactive.getContent())
                .extracting(Product::getIsVariantChild)
                .containsOnly(false);
        assertThat(inactive.getTotalElements()).isEqualTo(1);
    }

    @Test
    @DisplayName("семья целиком активна — в «Неактивные» не попадает")
    void shouldNotShowFullyActiveFamily() {
        Product parent = saveParent("Каска", true);
        saveChild("Каска M", true, parent.getId());

        Page<Product> inactive = productRepository.findFamiliesByActive(false, PageRequest.of(0, 20));

        assertThat(inactive.getContent()).isEmpty();
    }

    @Test
    @DisplayName("фильтр по категории учитывает вариант так же")
    void shouldApplyFamilyRuleWithinCategory() {
        Product parent = saveParent("Куртка", true);
        saveChild("Куртка 46", false, parent.getId());

        Page<Product> inactive = productRepository.findFamiliesByCategoryAndActive(
                category.getId(), false, PageRequest.of(0, 20));

        assertThat(inactive.getContent()).extracting(Product::getId).containsExactly(parent.getId());
    }
}
