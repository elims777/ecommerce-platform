package ru.rfsnab.gatewayservice.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Глобальный фильтр логирования.
 * Логирует входящие запросы и результат обработки с временем выполнения.
 */
@Slf4j
@Component
public class LoggingFilter implements GlobalFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String method = request.getMethod().name();
        String path = request.getURI().getPath();
        String ip = request.getRemoteAddress() != null
                ? request.getRemoteAddress().getAddress().getHostAddress()
                : "unknown";
        String userAgent = request.getHeaders().getFirst("User-Agent");

        long startTime = System.currentTimeMillis();

        log.info("→ {} {} | IP: {} | UA: {}", method, path, ip, userAgent);

        return chain.filter(exchange)
                .then(Mono.fromRunnable(() -> {
                    long duration = System.currentTimeMillis() - startTime;
                    int statusCode = exchange.getResponse().getStatusCode() != null
                            ? exchange.getResponse().getStatusCode().value()
                            : 0;

                    if (statusCode >= 500) {
                        log.error("← {} {} | {} | {}ms | IP: {}", method, path, statusCode, duration, ip);
                    } else if (statusCode >= 400) {
                        log.warn("← {} {} | {} | {}ms | IP: {}", method, path, statusCode, duration, ip);
                    } else {
                        log.info("← {} {} | {} | {}ms", method, path, statusCode, duration);
                    }
                }));
    }

    /**
     * Выполняется ПОСЛЕ JwtAuthenticationFilter (-100),
     * но перед остальными фильтрами.
     */
    @Override
    public int getOrder() {
        return -50;
    }
}
