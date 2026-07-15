package ru.rfsnab.productservice.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import ru.rfsnab.productservice.BaseIntegrationTest;
import ru.rfsnab.productservice.model.Category;
import ru.rfsnab.productservice.model.Product;
import ru.rfsnab.productservice.model.ProductAttribute;
import ru.rfsnab.productservice.spec.ProductSpecifications;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Интеграционные тесты ProductSpecifications.categoryWithAttributes на реальном PostgreSQL
 * (EXISTS-подзапрос, AND между свойствами / OR внутри свойства, id-тайбрейкер пагинации).
 */
@DisplayName("ProductSpecifications.categoryWithAttributes Integration")
class ProductSpecificationsIntegrationTest extends BaseIntegrationTest {

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

    private Product saveProduct(String name, boolean isActive, boolean isVariantChild,
                                 Map<String, String> attrs) {
        Product product = Product.builder()
                .name(name)
                .slug(name.toLowerCase().replace(" ", "-") + "-" + System.nanoTime())
                .category(category)
                .isActive(isActive)
                .isVariantChild(isVariantChild)
                .stockQuantity(10)
                .build();
        attrs.forEach((attrName, attrValue) -> product.getAttributes().add(
                ProductAttribute.builder()
                        .product(product)
                        .attributeName(attrName)
                        .attributeValue(attrValue)
                        .build()));
        return productRepository.save(product);
    }

    @Nested
    @DisplayName("AND между свойствами")
    class AndBetweenProperties {

        @Test
        @DisplayName("товар проходит только если удовлетворяет ОБОИМ выбранным свойствам")
        void shouldMatchOnlyWhenBothPropertiesSatisfied() {
            Product matches = saveProduct("Куртка синяя 48", true, false,
                    Map.of("Цвет", "Синий", "Размер", "48"));
            saveProduct("Куртка синяя 50", true, false,
                    Map.of("Цвет", "Синий", "Размер", "50"));
            saveProduct("Куртка красная 48", true, false,
                    Map.of("Цвет", "Красный", "Размер", "48"));

            Map<String, List<String>> filters = Map.of(
                    "Цвет", List.of("Синий"),
                    "Размер", List.of("48"));

            Page<Product> result = productRepository.findAll(
                    ProductSpecifications.categoryWithAttributes(List.of(category.getId()), filters),
                    PageRequest.of(0, 10));

            assertThat(result.getContent()).extracting(Product::getId)
                    .containsExactly(matches.getId());
        }
    }

    @Nested
    @DisplayName("OR внутри свойства")
    class OrWithinProperty {

        @Test
        @DisplayName("товар с любым из выбранных значений одного свойства проходит")
        void shouldMatchAnyOfSelectedValues() {
            Product blue = saveProduct("Куртка синяя", true, false, Map.of("Цвет", "Синий"));
            Product red = saveProduct("Куртка красная", true, false, Map.of("Цвет", "Красный"));
            saveProduct("Куртка зелёная", true, false, Map.of("Цвет", "Зелёный"));

            Map<String, List<String>> filters = Map.of("Цвет", List.of("Синий", "Красный"));

            Page<Product> result = productRepository.findAll(
                    ProductSpecifications.categoryWithAttributes(List.of(category.getId()), filters),
                    PageRequest.of(0, 10));

            assertThat(result.getContent()).extracting(Product::getId)
                    .containsExactlyInAnyOrder(blue.getId(), red.getId());
        }
    }

    @Nested
    @DisplayName("Несовпадение")
    class NoMatch {

        @Test
        @DisplayName("пустой результат, если ни один товар не подходит под фильтр")
        void shouldReturnEmptyWhenNoProductMatches() {
            saveProduct("Куртка синяя", true, false, Map.of("Цвет", "Синий"));

            Map<String, List<String>> filters = Map.of("Цвет", List.of("Жёлтый"));

            Page<Product> result = productRepository.findAll(
                    ProductSpecifications.categoryWithAttributes(List.of(category.getId()), filters),
                    PageRequest.of(0, 10));

            assertThat(result.getContent()).isEmpty();
        }
    }

    @Nested
    @DisplayName("Пагинация с id-тайбрейкером")
    class PaginationWithIdTiebreaker {

        @Test
        @DisplayName("2 страницы: порядок по id, без пропусков и дублей")
        void shouldPaginateDeterministicallyById() {
            List<Long> ids = new ArrayList<>();
            for (int i = 1; i <= 5; i++) {
                ids.add(saveProduct("Куртка " + i, true, false, Map.of("Цвет", "Синий")).getId());
            }

            Map<String, List<String>> filters = Map.of("Цвет", List.of("Синий"));

            Page<Product> page0 = productRepository.findAll(
                    ProductSpecifications.categoryWithAttributes(List.of(category.getId()), filters),
                    PageRequest.of(0, 3, Sort.by("id")));
            Page<Product> page1 = productRepository.findAll(
                    ProductSpecifications.categoryWithAttributes(List.of(category.getId()), filters),
                    PageRequest.of(1, 3, Sort.by("id")));

            assertThat(page0.getContent()).extracting(Product::getId)
                    .containsExactly(ids.get(0), ids.get(1), ids.get(2));
            assertThat(page1.getContent()).extracting(Product::getId)
                    .containsExactly(ids.get(3), ids.get(4));
            assertThat(page0.getTotalElements()).isEqualTo(5);
        }
    }

    @Nested
    @DisplayName("Исключение вариантов-детей и неактивных")
    class ExcludesVariantChildrenAndInactive {

        @Test
        @DisplayName("фильтр не захватывает isVariantChild=true и isActive=false")
        void shouldExcludeVariantChildrenAndInactiveProducts() {
            Product parent = saveProduct("Куртка родитель", true, false, Map.of("Цвет", "Синий"));
            saveProduct("Куртка вариант", true, true, Map.of("Цвет", "Синий"));
            saveProduct("Куртка неактивная", false, false, Map.of("Цвет", "Синий"));

            Map<String, List<String>> filters = Map.of("Цвет", List.of("Синий"));

            Page<Product> result = productRepository.findAll(
                    ProductSpecifications.categoryWithAttributes(List.of(category.getId()), filters),
                    PageRequest.of(0, 10));

            assertThat(result.getContent()).extracting(Product::getId)
                    .containsExactly(parent.getId());
        }
    }
}
