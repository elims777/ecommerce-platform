package ru.rfsnab.productservice.configuration;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Configuration;

/**
 * Кэш — это ускорение чтения, он не должен ронять бизнес-операции.
 * Если Redis недоступен, ошибки кэша (get/put/evict/clear) логируются и гасятся:
 * фасеты в этом случае просто считаются из БД, а импорт/листинг не падают.
 */
@Slf4j
@Configuration
@EnableCaching
public class CacheConfig implements CachingConfigurer {
    /** Имя кэша списка фасетов по категории. */
    public static final String FACETS_CACHE = "facets";

    @Override
    public CacheErrorHandler errorHandler() {
        return new CacheErrorHandler() {
            @Override
            public void handleCacheGetError(RuntimeException exception, Cache cache, Object key) {
                log.warn("Ошибка чтения кэша '{}' (key={}), продолжаем без кэша: {}",
                        cache.getName(), key, exception.getMessage());
            }

            @Override
            public void handleCachePutError(RuntimeException exception, Cache cache, Object key, Object value) {
                log.warn("Ошибка записи в кэш '{}' (key={}), продолжаем без кэша: {}",
                        cache.getName(), key, exception.getMessage());
            }

            @Override
            public void handleCacheEvictError(RuntimeException exception, Cache cache, Object key) {
                log.warn("Ошибка инвалидации кэша '{}' (key={}), пропускаем: {}",
                        cache.getName(), key, exception.getMessage());
            }

            @Override
            public void handleCacheClearError(RuntimeException exception, Cache cache) {
                log.warn("Ошибка очистки кэша '{}', пропускаем: {}",
                        cache.getName(), exception.getMessage());
            }
        };
    }
}
