package ru.rfsnab.gatewayservice;

import com.github.tomakehurst.wiremock.client.WireMock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.*;

/**
 * Интеграционные тесты gateway-service.
 * Проверяет роутинг, JWT фильтр и rate limiting.
 */
public class GatewayRoutingTest extends BaseIntegrationTest {
    @BeforeEach
    void setupWireMock() {
        wireMock.resetAll();

        // Стаб для всех downstream сервисов
        wireMock.stubFor(WireMock.any(anyUrl())
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"status\":\"ok\"}")));
    }

    //РОУТИНГ

    @Nested
    @DisplayName("Routing — маршрутизация запросов")
    class RoutingTests {
        @Test
        @DisplayName("Публичный endpoint без токена — 200")
        void publicEndpointWithoutToken_ShouldPass() {
            webTestClient.get()
                    .uri("/api/v1/products")
                    .exchange()
                    .expectBody(String.class)
                    .consumeWith(result -> System.out.println("BODY: " + result.getResponseBody()));
        }

        @Test
        @DisplayName("GET /api/v1/products — проксируется на product-service")
        void shouldRouteProductsRequest() {
            webTestClient.get()
                    .uri("/api/v1/products")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody().jsonPath("$.status").isEqualTo("ok");

            wireMock.verify(getRequestedFor(urlEqualTo("/api/v1/products")));
        }

        @Test
        @DisplayName("GET /api/v1/categories/tree — проксируется на product-service")
        void shouldRouteCategoriesRequest() {
            webTestClient.get()
                    .uri("/api/v1/categories/tree")
                    .exchange()
                    .expectStatus().isOk();

            wireMock.verify(getRequestedFor(urlEqualTo("/api/v1/categories/tree")));
        }

        @Test
        @DisplayName("GET /api/v1/orders — с токеном проксируется на order-service")
        void shouldRouteOrdersRequest() {
            String token = generateValidToken(1L, "test@test.com", List.of("ROLE_USER"));

            webTestClient.get()
                    .uri("/api/v1/orders")
                    .header("Authorization", "Bearer " + token)
                    .exchange()
                    .expectStatus().isOk();

            wireMock.verify(getRequestedFor(urlEqualTo("/api/v1/orders")));
        }

        @Test
        @DisplayName("GET /api/v1/cart — с токеном проксируется на order-service")
        void shouldRouteCartRequest() {
            String token = generateValidToken(1L, "test@test.com", List.of("ROLE_USER"));

            webTestClient.get()
                    .uri("/api/v1/cart")
                    .header("Authorization", "Bearer " + token)
                    .exchange()
                    .expectStatus().isOk();

            wireMock.verify(getRequestedFor(urlEqualTo("/api/v1/cart")));
        }

        @Test
        @DisplayName("Несуществующий маршрут — 404")
        void shouldReturn404ForUnknownRoute() {
            webTestClient.get()
                    .uri("/api/v1/unknown")
                    .exchange()
                    .expectStatus().isNotFound();
        }
    }

    //JWT ФИЛЬТР

    @Nested
    @DisplayName("JWT Filter — аутентификация")
    class JwtFilterTests {

        @Test
        @DisplayName("Публичный endpoint без токена — 200")
        void publicEndpointWithoutToken_ShouldPass() {
            webTestClient.get()
                    .uri("/api/v1/products")
                    .exchange()
                    .expectStatus().isOk();
        }

        @Test
        @DisplayName("Публичный endpoint GET /api/v1/categories — 200")
        void publicCategoriesWithoutToken_ShouldPass() {
            webTestClient.get()
                    .uri("/api/v1/categories")
                    .exchange()
                    .expectStatus().isOk();
        }

        @Test
        @DisplayName("Защищённый endpoint без токена — 401")
        void protectedEndpointWithoutToken_ShouldReturn401() {
            webTestClient.get()
                    .uri("/api/v1/orders")
                    .exchange()
                    .expectStatus().isUnauthorized();
        }

        @Test
        @DisplayName("Защищённый endpoint с валидным токеном — 200")
        void protectedEndpointWithValidToken_ShouldPass() {
            String token = generateValidToken(1L, "test@test.com", List.of("ROLE_USER"));

            webTestClient.get()
                    .uri("/api/v1/orders")
                    .header("Authorization", "Bearer " + token)
                    .exchange()
                    .expectStatus().isOk();
        }

        @Test
        @DisplayName("Защищённый endpoint с истёкшим токеном — 401")
        void protectedEndpointWithExpiredToken_ShouldReturn401() {
            String token = generateExpiredToken(1L, "test@test.com");

            webTestClient.get()
                    .uri("/api/v1/orders")
                    .header("Authorization", "Bearer " + token)
                    .exchange()
                    .expectStatus().isUnauthorized();
        }

        @Test
        @DisplayName("Защищённый endpoint с невалидным токеном — 401")
        void protectedEndpointWithInvalidToken_ShouldReturn401() {
            webTestClient.get()
                    .uri("/api/v1/orders")
                    .header("Authorization", "Bearer invalid.token.here")
                    .exchange()
                    .expectStatus().isUnauthorized();
        }

        @Test
        @DisplayName("Защищённый endpoint без Bearer префикса — 401")
        void protectedEndpointWithoutBearerPrefix_ShouldReturn401() {
            String token = generateValidToken(1L, "test@test.com", List.of("ROLE_USER"));

            webTestClient.get()
                    .uri("/api/v1/orders")
                    .header("Authorization", token)
                    .exchange()
                    .expectStatus().isUnauthorized();
        }

        @Test
        @DisplayName("POST /api/v1/products без токена — 401 (не GET)")
        void postProductWithoutToken_ShouldReturn401() {
            webTestClient.post()
                    .uri("/api/v1/products")
                    .header("Content-Type", "application/json")
                    .bodyValue("{\"name\":\"test\"}")
                    .exchange()
                    .expectStatus().isUnauthorized();
        }

        @Test
        @DisplayName("X-User-Email пробрасывается в downstream сервис")
        void shouldForwardUserEmailHeader() {
            String token = generateValidToken(1L, "test@test.com", List.of("ROLE_USER"));

            webTestClient.get()
                    .uri("/api/v1/orders")
                    .header("Authorization", "Bearer " + token)
                    .exchange()
                    .expectStatus().isOk();

            wireMock.verify(getRequestedFor(urlEqualTo("/api/v1/orders"))
                    .withHeader("X-User-Email", equalTo("test@test.com")));
        }
    }

    //RATE LIMITING

    @Nested
    @DisplayName("Rate Limiting — ограничение запросов")
    class RateLimitingTests {

        @Test
        @DisplayName("POST /v1/auth/login — после превышения лимита возвращает 429")
        void loginRateLimiting_ShouldReturn429() {
            wireMock.stubFor(WireMock.post(urlEqualTo("/v1/auth/login"))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withBody("{\"token\":\"test\"}")));

            // Отправляем запросы до превышения лимита (burst = 7)
            for (int i = 0; i < 8; i++) {
                webTestClient.post()
                        .uri("/v1/auth/login")
                        .header("Content-Type", "application/json")
                        .bodyValue("{\"email\":\"test@test.com\",\"password\":\"pass\"}")
                        .exchange();
            }

            // Следующий запрос должен получить 429
            webTestClient.post()
                    .uri("/v1/auth/login")
                    .header("Content-Type", "application/json")
                    .bodyValue("{\"email\":\"test@test.com\",\"password\":\"pass\"}")
                    .exchange()
                    .expectStatus().isEqualTo(429);
        }
    }
}
