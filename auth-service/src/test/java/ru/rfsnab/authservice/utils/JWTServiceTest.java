package ru.rfsnab.authservice.utils;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.rfsnab.authservice.configuration.JWTProperties;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("JWTService unit tests")
class JWTServiceTest {

    private JWTService jwtService;

    private static final String TEST_SECRET = "test-secret-key-for-jwt-signing-must-be-at-least-256-bits-long";
    private static final long EXPIRATION_MS = 3600000;
    private static final long REFRESH_EXPIRATION_MS = 86400000;

    // ДОБАВЛЕНО: тестовые данные
    private static final Long USER_ID = 1L;
    private static final String USER_EMAIL = "test@example.com";
    private static final List<String> USER_ROLES = List.of("ROLE_USER");
    private static final List<String> ADMIN_ROLES = List.of("ROLE_ADMIN", "ROLE_USER");

    @BeforeEach
    void setUp() {
        JWTProperties jwtProperties = new JWTProperties(TEST_SECRET, EXPIRATION_MS, REFRESH_EXPIRATION_MS);
        jwtService = new JWTService(jwtProperties);
    }

    @Test
    @DisplayName("generateToken() - генерирует валидный токен")
    void generateToken_ValidInput_ReturnsToken() {
        // ИЗМЕНЕНО: добавлен userId
        String token = jwtService.generateToken(USER_ID, USER_EMAIL, ADMIN_ROLES);

        assertThat(token).isNotNull();
        assertThat(token).isNotBlank();
        assertThat(token.split("\\.")).hasSize(3); // header.payload.signature
    }

    @Test
    @DisplayName("generateToken() - токен содержит правильный userId в subject")
    void generateToken_ContainsCorrectUserId() {
        String token = jwtService.generateToken(USER_ID, USER_EMAIL, USER_ROLES);

        Long extractedUserId = jwtService.extractUserId(token);

        assertThat(extractedUserId).isEqualTo(USER_ID);
    }

    @Test
    @DisplayName("generateToken() - токен содержит правильный email в claim")
    void generateToken_ContainsCorrectEmail() {
        String token = jwtService.generateToken(USER_ID, USER_EMAIL, USER_ROLES);

        String extractedEmail = jwtService.extractEmail(token);

        assertThat(extractedEmail).isEqualTo(USER_EMAIL);
    }

    @Test
    @DisplayName("generateToken() - токен содержит правильные роли")
    void generateToken_ContainsCorrectRoles() {
        String token = jwtService.generateToken(USER_ID, USER_EMAIL, ADMIN_ROLES);

        List<String> rolesFromToken = jwtService.extractRolesFromToken(token);

        assertThat(rolesFromToken).containsExactlyInAnyOrderElementsOf(ADMIN_ROLES);
    }

    @Test
    @DisplayName("generateRefreshToken() - генерирует валидный refresh токен")
    void generateRefreshToken_ValidInput_ReturnsToken() {
        // ИЗМЕНЕНО: добавлен userId
        String refreshToken = jwtService.generateRefreshToken(USER_ID, USER_EMAIL, USER_ROLES);

        assertThat(refreshToken).isNotNull();
        assertThat(refreshToken).isNotBlank();
        assertThat(jwtService.validateToken(refreshToken)).isTrue();
    }

    @Test
    @DisplayName("generateRefreshToken() - содержит правильные данные")
    void generateRefreshToken_ContainsCorrectData() {
        String refreshToken = jwtService.generateRefreshToken(USER_ID, USER_EMAIL, ADMIN_ROLES);

        assertThat(jwtService.extractUserId(refreshToken)).isEqualTo(USER_ID);
        assertThat(jwtService.extractEmail(refreshToken)).isEqualTo(USER_EMAIL);
        assertThat(jwtService.extractRolesFromToken(refreshToken)).containsExactlyInAnyOrderElementsOf(ADMIN_ROLES);
    }

    @Test
    @DisplayName("validateToken() - валидный токен возвращает true")
    void validateToken_ValidToken_ReturnsTrue() {
        String token = jwtService.generateToken(USER_ID, USER_EMAIL, USER_ROLES);

        boolean isValid = jwtService.validateToken(token);

        assertThat(isValid).isTrue();
    }

    @Test
    @DisplayName("validateToken() - невалидный токен возвращает false")
    void validateToken_InvalidToken_ReturnsFalse() {
        String invalidToken = "invalid.token.here";

        boolean isValid = jwtService.validateToken(invalidToken);

        assertThat(isValid).isFalse();
    }

    @Test
    @DisplayName("validateToken() - пустой токен возвращает false")
    void validateToken_EmptyToken_ReturnsFalse() {
        boolean isValid = jwtService.validateToken("");

        assertThat(isValid).isFalse();
    }

    @Test
    @DisplayName("validateToken() - null токен возвращает false")
    void validateToken_NullToken_ReturnsFalse() {
        boolean isValid = jwtService.validateToken(null);

        assertThat(isValid).isFalse();
    }

    @Test
    @DisplayName("extractUserId() - извлекает userId из токена")
    void extractUserId_ValidToken_ReturnsUserId() {
        Long expectedUserId = 42L;
        String token = jwtService.generateToken(expectedUserId, USER_EMAIL, USER_ROLES);

        Long extractedUserId = jwtService.extractUserId(token);

        assertThat(extractedUserId).isEqualTo(expectedUserId);
    }

    @Test
    @DisplayName("extractEmail() - извлекает email из токена")
    void extractEmail_ValidToken_ReturnsEmail() {
        String token = jwtService.generateToken(USER_ID, USER_EMAIL, USER_ROLES);

        String extractedEmail = jwtService.extractEmail(token);

        assertThat(extractedEmail).isEqualTo(USER_EMAIL);
    }

    @Test
    @DisplayName("extractRolesFromToken() - извлекает роли из токена")
    void extractRolesFromToken_ValidToken_ReturnsRoles() {
        String token = jwtService.generateToken(USER_ID, USER_EMAIL, ADMIN_ROLES);

        List<String> extractedRoles = jwtService.extractRolesFromToken(token);

        assertThat(extractedRoles).containsExactlyInAnyOrderElementsOf(ADMIN_ROLES);
    }

    @Test
    @DisplayName("isTokenExpired() - свежий токен не истёк")
    void isTokenExpired_FreshToken_ReturnsFalse() {
        String token = jwtService.generateToken(USER_ID, USER_EMAIL, USER_ROLES);

        boolean isExpired = jwtService.isTokenExpired(token);

        assertThat(isExpired).isFalse();
    }

    @Test
    @DisplayName("isTokenExpired() - истёкший токен возвращает true")
    void isTokenExpired_ExpiredToken_ReturnsTrue() {
        JWTProperties expiredProperties = new JWTProperties(TEST_SECRET, 0, 0);
        JWTService expiredJwtService = new JWTService(expiredProperties);

        // ИЗМЕНЕНО: добавлен userId
        String token = expiredJwtService.generateToken(USER_ID, USER_EMAIL, USER_ROLES);

        boolean isExpired = expiredJwtService.isTokenExpired(token);

        assertThat(isExpired).isTrue();
    }
}