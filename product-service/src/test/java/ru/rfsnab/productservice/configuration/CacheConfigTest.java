package ru.rfsnab.productservice.configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.cache.RedisCacheManager.RedisCacheManagerBuilder;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.JdkSerializationRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;
import ru.rfsnab.productservice.dto.FacetDto;
import ru.rfsnab.productservice.dto.ProductStatsResponse;

import java.nio.ByteBuffer;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * Значения кэшей в Redis — типизированный JSON; JDK-сериализованные байты не десериализуются.
 */
@DisplayName("CacheConfig: JSON-сериализация Redis-кэшей")
class CacheConfigTest {

    private RedisCacheManagerBuilder builder;

    @BeforeEach
    void setUp() {
        builder = RedisCacheManager.builder(mock(RedisConnectionFactory.class));
        new CacheConfig().redisJsonCacheCustomizer(new ObjectMapper()).customize(builder);
    }

    @SuppressWarnings("unchecked")
    private SerializationPair<Object> valuePair(String cacheName) {
        return (SerializationPair<Object>) builder.getCacheConfigurationFor(cacheName)
                .orElseThrow().getValueSerializationPair();
    }

    @Test
    @DisplayName("фасеты: запись и чтение возвращают равный список")
    void facetsRoundTrip() {
        SerializationPair<Object> pair = valuePair(CacheConfig.FACETS_CACHE);
        List<FacetDto> facets = List.of(new FacetDto("Цвет", List.of("красный", "синий")));

        Object read = pair.read(pair.write(facets));

        assertThat(read).isEqualTo(facets);
    }

    @Test
    @DisplayName("сводка: запись и чтение возвращают равный объект")
    void statsRoundTrip() {
        SerializationPair<Object> pair = valuePair(CacheConfig.PRODUCT_STATS_CACHE);
        ProductStatsResponse stats = new ProductStatsResponse(1, 2, 3, 4, 5, 6);

        Object read = pair.read(pair.write(stats));

        assertThat(read).isEqualTo(stats);
    }

    @Test
    @DisplayName("JDK-сериализованные байты не десериализуются")
    void jdkBytesAreRejected() {
        byte[] jdkBytes = new JdkSerializationRedisSerializer().serialize("x");
        SerializationPair<Object> pair = valuePair(CacheConfig.FACETS_CACHE);

        assertThatThrownBy(() -> pair.read(ByteBuffer.wrap(jdkBytes)))
                .isInstanceOf(RuntimeException.class);
    }
}
