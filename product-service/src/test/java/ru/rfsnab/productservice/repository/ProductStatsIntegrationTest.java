package ru.rfsnab.productservice.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import ru.rfsnab.productservice.BaseIntegrationTest;
import ru.rfsnab.productservice.configuration.CacheConfig;
import ru.rfsnab.productservice.dto.ProductStatsResponse;
import ru.rfsnab.productservice.model.Product;
import ru.rfsnab.productservice.repository.projection.ProductStatsProjection;
import ru.rfsnab.productservice.service.ProductService;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Сводка по каталогу для админки на реальном PostgreSQL.
 * Товары 1С не проставляют source (остаётся дефолт INTERNAL), поэтому "с 1С" = все, кроме FTK.
 */
@DisplayName("Сводка по каталогу (stats) Integration")
class ProductStatsIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductService productService;

    @Autowired
    private CacheManager cacheManager;

    @BeforeEach
    void setUp() {
        productRepository.deleteAll();
        // Сводка кэшируется, а кэш живёт на весь контекст: без сброса соседний тест
        // получит чужие цифры. В бою этот сброс делает импорт.
        // Имя явно: getCacheNames() у ленивых менеджеров пуст до первого обращения.
        Cache statsCache = cacheManager.getCache(CacheConfig.PRODUCT_STATS_CACHE);
        assertThat(statsCache).as("кэш сводки должен существовать, иначе тест ничего не изолирует").isNotNull();
        statsCache.clear();
    }

    private void saveProduct(String name, String source, boolean isActive, boolean isVariantChild) {
        productRepository.save(Product.builder()
                .name(name)
                .slug(name.toLowerCase().replace(" ", "-") + "-" + System.nanoTime())
                .source(source)
                .isActive(isActive)
                .isVariantChild(isVariantChild)
                .stockQuantity(0)
                .build());
    }

    @Test
    @DisplayName("считает уникальные, всего, по источникам и по активности")
    void shouldCountCatalogStats() {
        // ФТК: родитель активный + 2 активных варианта + 1 неактивный вариант
        saveProduct("Куртка ФТК", "FTK", true, false);
        saveProduct("Куртка ФТК 44", "FTK", true, true);
        saveProduct("Куртка ФТК 46", "FTK", true, true);
        saveProduct("Куртка ФТК 48", "FTK", false, true);
        // ФТК: неактивный родитель без вариантов
        saveProduct("Каска ФТК", "FTK", false, false);
        // 1С (source остаётся INTERNAL): 2 активных + 1 неактивный, вариантов у 1С нет
        saveProduct("Перчатки 1С", "INTERNAL", true, false);
        saveProduct("Ботинки 1С", "INTERNAL", true, false);
        saveProduct("Очки 1С", "INTERNAL", false, false);

        ProductStatsProjection stats = productRepository.fetchProductStats();

        assertThat(stats.getTotal()).isEqualTo(8);
        assertThat(stats.getUniqueProducts()).isEqualTo(5);
        assertThat(stats.getFtk()).isEqualTo(5);
        assertThat(stats.getActive()).isEqualTo(5);
    }

    @Test
    @DisplayName("сервис выводит товары 1С и неактивные вычитанием")
    void shouldMapProjectionToResponse() {
        saveProduct("Куртка ФТК", "FTK", true, false);
        saveProduct("Куртка ФТК 44", "FTK", true, true);
        saveProduct("Каска ФТК", "FTK", false, false);
        saveProduct("Перчатки 1С", "INTERNAL", true, false);
        saveProduct("Очки 1С", "INTERNAL", false, false);

        ProductStatsResponse response = productService.getProductStats();

        assertThat(response.uniqueProducts()).isEqualTo(4);
        assertThat(response.total()).isEqualTo(5);
        assertThat(response.fromFtk()).isEqualTo(3);
        assertThat(response.fromOneC()).isEqualTo(2);
        assertThat(response.active()).isEqualTo(3);
        assertThat(response.inactive()).isEqualTo(2);
    }

    @Test
    @DisplayName("на пустом каталоге отдаёт нули, а не падает")
    void shouldReturnZerosOnEmptyCatalog() {
        ProductStatsResponse response = productService.getProductStats();

        assertThat(response.uniqueProducts()).isZero();
        assertThat(response.total()).isZero();
        assertThat(response.fromOneC()).isZero();
        assertThat(response.fromFtk()).isZero();
        assertThat(response.active()).isZero();
        assertThat(response.inactive()).isZero();
    }
}
