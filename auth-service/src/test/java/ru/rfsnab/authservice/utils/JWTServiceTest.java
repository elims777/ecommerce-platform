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

    private final static String TEST_SECRET = "test-secret-key-for-jwt-signing-must-be-at-least-256-bits-long";
    private final static long EXPIRATION_MS = 3600000;
    private final static long REFRESH_EXPIRATION_MS = 86400000;

    @BeforeEach
    void setUp(){
        JWTProperties jwtProperties = new JWTProperties(TEST_SECRET, EXPIRATION_MS, REFRESH_EXPIRATION_MS);
        jwtService = new JWTService(jwtProperties);
    }

    @Test
    @DisplayName("generateToken() - генерирует валидный токен")
    void generateToken_ValidInput_ReturnsToken(){
        String email = "test@example.com";
        List<String> roles = List.of("ROLE_ADMIN", "ROLE_USER");

        String token = jwtService.generateToken(email, roles);

        assertThat(token).isNotNull();
        assertThat(token).isNotBlank();
        assertThat(token.split("\\.")).hasSize(3); //header.payload.signature
    }

    @Test
    @DisplayName("generateToken() - токен содержит правильный email")
    void generateToken_ContainsCorrectEmail(){
        String email = "test@example.com";
        List<String> roles = List.of("ROLE_USER");

        String token = jwtService.generateToken(email, roles);
        String extractEmail = jwtService.extractEmail(token);

        assertThat(extractEmail).isEqualTo(email);
    }

    @Test
    @DisplayName("generateToken() - токен содержит правильные роли")
    void generateToken_ContainsCorrectRoles(){
        String email = "test@example.com";
        List<String> roles = List.of("ROLE_ADMIN", "ROLE_USER");

        String token = jwtService.generateToken(email, roles);
        List<String> rolesFromToken = jwtService.extractRolesFromToken(token);

        assertThat(rolesFromToken).containsExactlyInAnyOrderElementsOf(roles);
    }

    @Test
    @DisplayName("generateRefreshToken() - генерирует валидный refresh токен")
    void generateRefreshToken_ValidInput_ReturnsToken(){
        String email = "test@example.com";
        List<String> roles = List.of("ROLE_USER");

        String refreshToken = jwtService.generateRefreshToken(email, roles);

        assertThat(refreshToken).isNotNull();
        assertThat(refreshToken).isNotBlank();
        assertThat(jwtService.validateToken(refreshToken)).isTrue();
    }

    @Test
    @DisplayName("validateToken() - валидный токен возвращает true")
    void validateToken_ValidToken_ReturnsTrue(){
        String email = "test@example.com";
        List<String> roles = List.of("ROLE_USER");

        String token = jwtService.generateToken(email, roles);

        boolean isValid = jwtService.validateToken(token);

        assertThat(isValid).isTrue();
    }

    @Test
    @DisplayName("validateToken() - невалидный токен возвращает false")
    void validateToken_InvalidToken_ReturnsFalse(){
        String invalidToken = "invalid.token.here";

        boolean isValid = jwtService.validateToken(invalidToken);

        assertThat(isValid).isFalse();
    }

    @Test
    @DisplayName("validateToken() - пустой токен возвращает false")
    void validateToken_EmptyToken_ReturnsFalse(){
        boolean isValid = jwtService.validateToken("");

        assertThat(isValid).isFalse();
    }

    @Test
    @DisplayName("validateToken() - null токен возвращает false")
    void validateToken_NullToken_ReturnsFalse(){
        boolean isValid = jwtService.validateToken(null);

        assertThat(isValid).isFalse();
    }

    @Test
    @DisplayName("extractEmail() - извлекает email из токена")
    void extractEmail_ValidToken_ReturnsEmail(){
        String email = "test@example.com";
        List<String> roles = List.of("ROLE_USER");

        String token = jwtService.generateToken(email, roles);
        String extractEmail = jwtService.extractEmail(token);

        assertThat(extractEmail).isEqualTo(email);
    }

    @Test
    @DisplayName("extractRolesFromToken() - извлекает роли из токена")
    void extractRolesFromToken_ValidToken_ReturnsRoles(){
        String email = "test@example.com";
        List<String> roles = List.of("ROLE_ADMIN", "ROLE_USER");

        String token = jwtService.generateToken(email, roles);
        List<String> extractedRoles = jwtService.extractRolesFromToken(token);

        assertThat(extractedRoles).containsExactlyInAnyOrderElementsOf(roles);
    }

    @Test
    @DisplayName("isTokenExpired() - свежий токен не истёк")
    void isTokenExpired_FreshToken_ReturnsFalse(){
        String email = "test@example.com";
        List<String> roles = List.of("ROLE_USER");

        String token = jwtService.generateToken(email, roles);
        boolean isExpired = jwtService.isTokenExpired(token);

        assertThat(isExpired).isFalse();
    }

    @Test
    @DisplayName("isTokenExpired() - истёкший токен возвращает true")
    void isTokenExpired_ExpiredToken_ReturnsTrue(){
        JWTProperties expiredProperties = new JWTProperties(TEST_SECRET, 0, 0);
        JWTService expiredJwtService = new JWTService(expiredProperties);
        String email = "test@example.com";
        List<String> roles = List.of("ROLE_USER");
        String token = expiredJwtService.generateToken(email, roles);

        boolean isExpired = expiredJwtService.isTokenExpired(token);

        assertThat(isExpired).isTrue();
    }
}