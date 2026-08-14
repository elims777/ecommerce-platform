package ru.rfsnab.productservice.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.cache.interceptor.SimpleKey;
import ru.rfsnab.productservice.configuration.CacheConfig;
import ru.rfsnab.productservice.dto.BatchProductImportRequest;
import ru.rfsnab.productservice.dto.ProductStatsResponse;
import ru.rfsnab.productservice.repository.CategoryRepository;
import ru.rfsnab.productservice.repository.ProductAttributeRepository;
import ru.rfsnab.productservice.repository.ProductRepository;
import ru.rfsnab.productservice.repository.projection.ProductStatsProjection;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Кэширование сводки по каталогу и его сброс импортом.
 * Кэш — in-memory: проверяется поведение аннотаций, а не Redis.
 * Импорт вызывается напрямую, чтобы триггер сработал на любом источнике (ФТК и 1С
 * приходят в один и тот же importBatch).
 */
@SpringBootTest(classes = {
        ProductService.class,
        ProductStatsCacheTest.CacheTestConfig.class
})
@DisplayName("Кэш сводки по каталогу")
class ProductStatsCacheTest {

    @EnableCaching
    static class CacheTestConfig {
        @Bean
        CacheManager cacheManager() {
            return new ConcurrentMapCacheManager(
                    CacheConfig.FACETS_CACHE, CacheConfig.PRODUCT_STATS_CACHE);
        }

    }

    @Autowired
    private ProductService productService;

    @Autowired
    private CacheManager cacheManager;

    @MockBean
    private ProductRepository productRepository;

    @MockBean
    private CategoryRepository categoryRepository;

    @MockBean
    private CategoryService categoryService;

    @MockBean
    private SlugGeneratorService slugGeneratorService;

    @MockBean
    private ProductAttributeRepository attributeRepository;

    @BeforeEach
    void setUp() {
        cacheManager.getCache(CacheConfig.PRODUCT_STATS_CACHE).clear();
        Mockito.reset(productRepository);

        ProductStatsProjection projection = Mockito.mock(ProductStatsProjection.class);
        when(projection.getTotal()).thenReturn(10L);
        when(projection.getUniqueProducts()).thenReturn(6L);
        when(projection.getFtk()).thenReturn(7L);
        when(projection.getActive()).thenReturn(8L);
        when(productRepository.fetchProductStats()).thenReturn(projection);
    }

    @Test
    @DisplayName("второй вызов берёт значение из кэша, БД не трогает")
    void shouldServeSecondCallFromCache() {
        productService.getProductStats();
        productService.getProductStats();

        verify(productRepository, times(1)).fetchProductStats();
    }

    @Test
    @DisplayName("после сброса кэша значение считается заново")
    void shouldRecountAfterEvict() {
        productService.getProductStats();
        verify(productRepository, times(1)).fetchProductStats();

        cacheManager.getCache(CacheConfig.PRODUCT_STATS_CACHE).clear();

        productService.getProductStats();
        verify(productRepository, times(2)).fetchProductStats();
    }

    @Test
    @DisplayName("сводка кладётся в кэш при первом обращении")
    void shouldPutStatsIntoCache() {
        assertThat(cacheManager.getCache(CacheConfig.PRODUCT_STATS_CACHE).get(SimpleKey.EMPTY)).isNull();

        ProductStatsResponse response = productService.getProductStats();

        assertThat(cacheManager.getCache(CacheConfig.PRODUCT_STATS_CACHE).get(SimpleKey.EMPTY).get())
                .isEqualTo(response);
    }

    @Test
    @DisplayName("импорт помечен сбросом кэша сводки — триггер на ФТК и 1С")
    void importBatchShouldBeAnnotatedWithStatsEvict() throws NoSuchMethodException {
        Caching caching = ProductImportService.class
                .getMethod("importBatch", BatchProductImportRequest.class)
                .getAnnotation(Caching.class);

        assertThat(caching).as("importBatch должен сбрасывать кэши").isNotNull();
        assertThat(caching.evict())
                .extracting(CacheEvict::value)
                .anySatisfy(caches -> assertThat(caches).contains(CacheConfig.PRODUCT_STATS_CACHE));
        assertThat(caching.evict())
                .filteredOn(evict -> List.of(evict.value()).contains(CacheConfig.PRODUCT_STATS_CACHE))
                .allSatisfy(evict -> assertThat(evict.allEntries()).isTrue());
    }

    @Test
    @DisplayName("значение из кэша совпадает с посчитанным")
    void shouldReturnSameValueFromCache() {
        ProductStatsResponse first = productService.getProductStats();
        ProductStatsResponse second = productService.getProductStats();

        assertThat(second).isEqualTo(first);
        assertThat(first.uniqueProducts()).isEqualTo(6);
        assertThat(first.total()).isEqualTo(10);
        assertThat(first.fromFtk()).isEqualTo(7);
        assertThat(first.fromOneC()).isEqualTo(3);
        assertThat(first.active()).isEqualTo(8);
        assertThat(first.inactive()).isEqualTo(2);
    }
}
