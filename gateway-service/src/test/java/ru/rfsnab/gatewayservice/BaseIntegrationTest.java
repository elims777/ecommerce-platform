package ru.rfsnab.gatewayservice;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;

/**
 * Базовый класс для интеграционных тестов gateway-service.
 * Поднимает Redis (Testcontainers или использует localhost в CI) и WireMock (имитация downstream сервисов).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@SuppressWarnings("resource")
@ActiveProfiles("test")
public class BaseIntegrationTest {
    private static final String JWT_SECRET = "test-secret-key-for-testing-must-be-at-least-256-bits-long-for-hs256";
    protected static WireMockServer wireMock;
    protected static GenericContainer<?> redis;
    private static boolean useLocalRedis = false;

    static {
        // Проверяем доступность Docker
        try {
            DockerClientFactory.instance().client();
            // Docker доступен — используем Testcontainers
            redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
                    .withExposedPorts(6379);
            redis.start();
        } catch (Exception e) {
            // Docker недоступен — используем localhost (CI environment)
            System.out.println("Docker not available, using localhost Redis (CI mode)");
            useLocalRedis = true;
        }

        // WireMock — фиксированный порт
        wireMock = new WireMockServer(WireMockConfiguration.wireMockConfig().port(19999));
        wireMock.start();
    }

    @Autowired
    protected WebTestClient webTestClient;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        // Redis
        if (useLocalRedis) {
            registry.add("spring.data.redis.host", () -> "localhost");
            registry.add("spring.data.redis.port", () -> 6379);
        } else {
            registry.add("spring.data.redis.host", redis::getHost);
            registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        }
    }

    /**
     * Генерация валидного JWT токена для тестов
     */
    protected String generateValidToken(Long userId, String email, List<String> roles) {
        SecretKey key = Keys.hmacShaKeyFor(JWT_SECRET.getBytes(StandardCharsets.UTF_8));

        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("email", email)
                .claim("roles", roles)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 3600000))
                .signWith(key)
                .compact();
    }

    /**
     * Генерация истёкшего JWT токена
     */
    protected String generateExpiredToken(Long userId, String email) {
        SecretKey key = Keys.hmacShaKeyFor(JWT_SECRET.getBytes(StandardCharsets.UTF_8));

        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("email", email)
                .issuedAt(new Date(System.currentTimeMillis() - 7200000))
                .expiration(new Date(System.currentTimeMillis() - 3600000))
                .signWith(key)
                .compact();
    }
}
