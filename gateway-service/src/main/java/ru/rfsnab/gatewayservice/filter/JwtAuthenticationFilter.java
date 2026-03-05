package ru.rfsnab.gatewayservice.filter;


import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Глобальный фильтр JWT аутентификации.
 * Первый рубеж защиты — проверяет валидность токена до того,
 * как запрос уйдёт в целевой сервис.
 * Публичные endpoint'ы пропускаются без проверки.
 */
@Slf4j
@Component
public class JwtAuthenticationFilter implements GlobalFilter, Ordered {

    private final SecretKey signingKey;

    private static final List<String> PUBLIC_PATHS = List.of(
            "/v1/auth/login",
            "/v1/auth/refresh",
            "/api/v1/register",
            "/auth/",
            "/auth/login-form",
            "/auth/register",
            "/oauth2/",
            "/verify-email",
            "/api/verification/",
            "/",
            "/api/v1/products",
            "/api/v1/categories"
    );


    public JwtAuthenticationFilter(@Value("${jwt.secret}") String secret) {
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }


    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();
        String method = request.getMethod().name();

        //Публичные эндпоинты не проверяют JWT
        if(isPublicPath(path, method)){
            return chain.filter(exchange);
        }

        String authHeader = request.getHeaders().getFirst("Authorization");
        if(authHeader == null || !authHeader.startsWith("Bearer ")){
            log.warn("Запрос без JWT токена: {}, {}", method, path);
            return rejectRequest(exchange, HttpStatus.UNAUTHORIZED);
        }

        String token = authHeader.substring(7);
        try{
            Claims claims = Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            String email = claims.get("email", String.class);

            // Пробрасываем данные пользователя в заголовках для downstream сервисов
            ServerHttpRequest modifiedRequest = request.mutate()
                    .header("X-User-Email", email)
                    .build();
            log.debug("JWT валиден для пользователя: {}, {} {}", email, method, path);

            return chain.filter(exchange.mutate().request(modifiedRequest).build());
        } catch (ExpiredJwtException e) {
            log.warn("Истёкший JWT токен: {} {}", method, path);
            return rejectRequest(exchange, HttpStatus.UNAUTHORIZED);

        } catch (SignatureException | MalformedJwtException e) {
            log.warn("Невалидный JWT токен: {} {} — {}", method, path, e.getMessage());
            return rejectRequest(exchange, HttpStatus.UNAUTHORIZED);

        } catch (Exception e) {
            log.error("Ошибка при валидации JWT: {} {} — {}", method, path, e.getMessage());
            return rejectRequest(exchange, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Проверка — является ли путь публичным.
     * GET запросы к каталогу (products, categories) — публичные.
     * POST/PUT/DELETE к ним — требуют авторизации.
     */
    private boolean isPublicPath(String path, String method) {
        // Главная страница — точное совпадение
        if ("/".equals(path)) {
            return true;
        }

        // Каталог и точки самовывоза — только GET
        if ("GET".equals(method) &&
                (path.startsWith("/api/v1/products")
                        || path.startsWith("/api/v1/categories")
                        || path.startsWith("/api/v1/warehouse-points"))) {
            return true;
        }

        // Остальные публичные пути (исключаем "/", products, categories — уже проверены выше)
        return PUBLIC_PATHS.stream()
                .filter(publicPath -> !"/".equals(publicPath)
                        && !"/api/v1/products".equals(publicPath)
                        && !"/api/v1/categories".equals(publicPath))
                .anyMatch(path::startsWith);
    }

    /**
     * Отклонение запроса с указанным статусом
     */
    private Mono<Void> rejectRequest(ServerWebExchange exchange, HttpStatus status){
        exchange.getResponse().setStatusCode(status);
        return exchange.getResponse().setComplete();
    }

    /**
     * Порядок выполнения — перед остальными фильтрами.
     * Чем меньше число, тем раньше выполняется.
     */
    @Override
    public int getOrder() {
        return -100;
    }
}
