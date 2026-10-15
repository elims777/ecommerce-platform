package ru.rfsnab.productservice.configuration;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.cache.RedisCacheManagerBuilderCustomizer;
import org.springframework.cache.Cache;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.RedisSerializer;
import ru.rfsnab.productservice.dto.FacetDto;
import ru.rfsnab.productservice.dto.ProductStatsResponse;

import java.util.List;

/**
 * Кэш — это ускорение чтения, он не должен ронять бизнес-операции.
 * Если Redis недоступен, ошибки кэша (get/put/evict/clear) логируются и гасятся:
 * фасеты в этом случае просто считаются из БД, а импорт/листинг не падают.
 * Значения в Redis хранятся как типизированный JSON, а не как JDK-сериализованные объекты.
 */
@Slf4j
@Configuration
@EnableCaching
public class CacheConfig implements CachingConfigurer {
    /** Имя кэша списка фасетов по категории. */
    public static final String FACETS_CACHE = "facets";

    /** Имя кэша сводки по каталогу для админки. Сбрасывается импортом товаров. */
    public static final String PRODUCT_STATS_CACHE = "productStats";

    // JDK-сериализация исполняет код из подложенных в Redis байтов; JSON с конкретным типом — нет.
    @Bean
    RedisCacheManagerBuilderCustomizer redisJsonCacheCustomizer(ObjectMapper objectMapper) {
        JavaType facets = objectMapper.getTypeFactory().constructCollectionType(List.class, FacetDto.class);
        return builder -> builder
                .withCacheConfiguration(FACETS_CACHE, jsonConfig(new Jackson2JsonRedisSerializer<>(objectMapper, facets)))
                .withCacheConfiguration(PRODUCT_STATS_CACHE,
                        jsonConfig(new Jackson2JsonRedisSerializer<>(objectMapper, ProductStatsResponse.class)));
    }

    private static RedisCacheConfiguration jsonConfig(RedisSerializer<?> serializer) {
        return RedisCacheConfiguration.defaultCacheConfig()
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(serializer));
    }

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
