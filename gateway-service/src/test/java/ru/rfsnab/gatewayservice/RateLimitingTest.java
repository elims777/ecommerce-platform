package ru.rfsnab.gatewayservice;

import com.github.tomakehurst.wiremock.client.WireMock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.util.List;
import java.util.function.IntFunction;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Интеграционные тесты rate limiting gateway-service.
 * Проверяет per-IP лимиты по X-Real-IP, общий (global) лимит маршрута и fallback на remoteAddress.
 */
public class RateLimitingTest extends BaseIntegrationTest {

    private static final String FORGOT_PASSWORD_URI = "/api/v1/auth/forgot-password";
    private static final String ORDERS_URI = "/api/v1/orders";
    private static final String GLOBAL_LIMIT_URI = "/api/verification/verify";
    private static final String LOGIN_URI = "/v1/auth/login";
    private static final String REAL_IP_HEADER = "X-Real-IP";
    private static final String FORWARDED_FOR_HEADER = "X-Forwarded-For";
    private static final String JSON_CONTENT_TYPE = "application/json";
    private static final int TOO_MANY_REQUESTS = 429;
    /** Лимитер считает токены со временем: при медленном CI "N+1-й запрос" не гарантирован, поэтому ищем 429 циклом */
    private static final int MAX_ATTEMPTS = 20;

    @Autowired
    private ReactiveRedisConnectionFactory redisConnectionFactory;

    @BeforeEach
    void setUp() {
        wireMock.resetAll();
        wireMock.stubFor(WireMock.any(anyUrl())
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", JSON_CONTENT_TYPE)
                        .withBody("{\"status\":\"ok\"}")));

        // Счётчики лимитов живут в Redis и переживают тесты — чистим перед каждым
        redisConnectionFactory.getReactiveConnection()
                .serverCommands()
                .flushDb()
                .block();
    }

    @Test
    @DisplayName("Per-IP: серия с одного X-Real-IP упирается в 429, другой IP не задет")
    void forgotPassword_perIpLimit_shouldBlockSameIp() {
        assertTrue(blockedWithin(i -> forgotPassword("10.10.10.1", null)),
                "per-IP лимит должен сработать в пределах " + MAX_ATTEMPTS + " запросов");
        assertEquals(HttpStatus.OK.value(), statusFor(forgotPassword("10.10.10.2", null)));
    }

    @Test
    @DisplayName("Подменный X-Forwarded-For не обходит лимит по X-Real-IP")
    void forgedXForwardedFor_shouldNotBypassIpLimit() {
        assertTrue(blockedWithin(i -> forgotPassword("10.10.10.3", "203.0.113." + i)),
                "лимит должен считаться по X-Real-IP, а не по X-Forwarded-For");
    }

    @Test
    @DisplayName("Общий лимит маршрута: серия запросов с разных IP упирается в 429")
    void globalLimit_shouldBlockBurstFromManyIps() {
        assertTrue(blockedWithin(i -> verify("10.20.0." + i)),
                "global-rate=1 должен сработать в пределах " + MAX_ATTEMPTS + " запросов");
    }

    @Test
    @DisplayName("POST /api/v1/orders: серия запросов с одного IP упирается в 429")
    void ordersCreate_perIpLimit_shouldReturn429() {
        String token = generateValidToken(1L, "test@test.com", List.of("ROLE_USER"));
        assertTrue(blockedWithin(i -> createOrder(token)),
                "per-IP лимит заказа должен сработать в пределах " + MAX_ATTEMPTS + " запросов");
    }

    @Test
    @DisplayName("Невалидный X-Real-IP падает в бакет remoteAddress, валидный — в отдельный")
    void invalidRealIp_shouldFallbackToRemoteAddress() {
        assertTrue(blockedWithin(i -> login(null)),
                "лимит по remoteAddress должен сработать в пределах " + MAX_ATTEMPTS + " запросов");

        // мусор в заголовке не становится ключом — тот же бакет, что и без заголовка
        assertEquals(TOO_MANY_REQUESTS, statusFor(login("not-an-ip")));
        // валидный IP — свой бакет
        assertEquals(HttpStatus.OK.value(), statusFor(login("10.99.99.99")));
    }

    /** Шлёт запросы, пока не получит 429; остальные ответы обязаны быть 200. */
    private boolean blockedWithin(IntFunction<WebTestClient.ResponseSpec> request) {
        for (int i = 0; i < MAX_ATTEMPTS; i++) {
            int status = statusFor(request.apply(i));
            if (status == TOO_MANY_REQUESTS) {
                return true;
            }
            assertEquals(HttpStatus.OK.value(), status);
        }
        return false;
    }

    private int statusFor(WebTestClient.ResponseSpec response) {
        return response.expectBody(String.class).returnResult().getStatus().value();
    }

    private WebTestClient.ResponseSpec forgotPassword(String realIp, String forwardedFor) {
        WebTestClient.RequestBodySpec request = webTestClient.post()
                .uri(FORGOT_PASSWORD_URI)
                .header("Content-Type", JSON_CONTENT_TYPE)
                .header(REAL_IP_HEADER, realIp);
        if (forwardedFor != null) {
            request.header(FORWARDED_FOR_HEADER, forwardedFor);
        }
        return request.bodyValue("{\"email\":\"test@test.com\"}").exchange();
    }

    private WebTestClient.ResponseSpec verify(String realIp) {
        return webTestClient.post()
                .uri(GLOBAL_LIMIT_URI)
                .header("Content-Type", JSON_CONTENT_TYPE)
                .header(REAL_IP_HEADER, realIp)
                .bodyValue("{\"token\":\"test\"}")
                .exchange();
    }

    private WebTestClient.ResponseSpec createOrder(String token) {
        return webTestClient.post()
                .uri(ORDERS_URI)
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", JSON_CONTENT_TYPE)
                .header(REAL_IP_HEADER, "10.30.0.1")
                .bodyValue("{}")
                .exchange();
    }

    private WebTestClient.ResponseSpec login(String realIp) {
        WebTestClient.RequestBodySpec request = webTestClient.post()
                .uri(LOGIN_URI)
                .header("Content-Type", JSON_CONTENT_TYPE);
        if (realIp != null) {
            request.header(REAL_IP_HEADER, realIp);
        }
        return request.bodyValue("{\"email\":\"test@test.com\",\"password\":\"pass\"}").exchange();
    }
}
