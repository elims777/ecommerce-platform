package ru.rfsnab.gatewayservice.filter;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.Ordered;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Общий (не per-IP) лимит запросов на маршрут — защита от флуда с множества адресов.
 * Порог задаётся в маршруте: metadata.global-rate (запросов в секунду).
 * Окно фиксированное (1 секунда): на границе секунд возможно до двух порогов подряд — для anti-flood приемлемо.
 * Отдельный фильтр, а не второй RequestRateLimiter: Spring Cloud Gateway хранит конфиг
 * лимитера по id маршрута, поэтому два RequestRateLimiter на одном маршруте затирают друг друга.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GlobalRateLimitFilter implements GlobalFilter, Ordered {

    private static final String LIMIT_METADATA_KEY = "global-rate";
    private static final String KEY_PREFIX = "gw:global-rate:";
    private static final Duration KEY_TTL = Duration.ofSeconds(2);

    /** INCR и PEXPIRE одним вызовом: атомарно и на один round-trip меньше. TTL нужен только для уборки ключей. */
    private static final RedisScript<Long> INCREMENT_WITH_TTL = new DefaultRedisScript<>(
            "local count = redis.call('INCR', KEYS[1]) "
                    + "if count == 1 then redis.call('PEXPIRE', KEYS[1], ARGV[1]) end "
                    + "return count",
            Long.class);

    private final ReactiveStringRedisTemplate redis;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        Route route = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
        Integer limit = route == null ? null : limitOf(route);
        if (limit == null) {
            return chain.filter(exchange);
        }
        String key = KEY_PREFIX + route.getId() + ":" + Instant.now().getEpochSecond();
        // аргументы сериализуются StringRedisSerializer — передаём TTL строкой
        return redis.execute(INCREMENT_WITH_TTL, List.of(key), List.of(String.valueOf(KEY_TTL.toMillis())))
                .next()
                .flatMap(count -> {
                    if (count > limit) {
                        // Одна строка на маршрут в секунду: блокировка видна в логах, но флуда в лог нет
                        if (count == limit + 1) {
                            log.warn("Global rate limit exceeded: route={}, limit={}/s", route.getId(), limit);
                        }
                        exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
                        return exchange.getResponse().setComplete();
                    }
                    return chain.filter(exchange);
                });
    }

    private static Integer limitOf(Route route) {
        Object value = route.getMetadata().get(LIMIT_METADATA_KEY);
        if (value == null) {
            return null;
        }
        if (value instanceof Integer limit) {
            return limit;
        }
        throw new IllegalStateException(
                "metadata." + LIMIT_METADATA_KEY + " must be an integer, route " + route.getId() + ": " + value);
    }

    /**
     * Раньше JWT-фильтра (-100): лимит считает и запросы без токена.
     */
    @Override
    public int getOrder() {
        return -200;
    }
}
