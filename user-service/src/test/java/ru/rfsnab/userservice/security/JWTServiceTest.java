package ru.rfsnab.userservice.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("JWTService unit tests")
public class JWTServiceTest {
    private JWTService jwtService;

    private static final String SECRET = "test-secret-key-for-testing-must-be-at-least-256-bits-long-for-hs256";
    private static final Long USER_ID = 42L;
    private static final String EMAIL = "test@example.com";
    private static final List<String> ROLES = List.of("ROLE_USER", "ROLE_ADMIN");

    @BeforeEach
    void setUp() {
        JWTProperties properties = new JWTProperties(SECRET);
        jwtService = new JWTService(properties);
    }

    private String generateToken(Long userId, String email, List<String> roles, long expirationMs) {
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        return Jwts.builder()
                .subject(userId.toString())
                .claim("email", email)
                .claim("roles", roles)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expirationMs))
                .signWith(key)
                .compact();
    }

    private String generateValidToken() {
        return generateToken(USER_ID, EMAIL, ROLES, 3600000); // 1 час
    }

    private String generateExpiredToken() {
        return generateToken(USER_ID, EMAIL, ROLES, -1000); // уже истёк
    }

    @Nested
    @DisplayName("extractUserId()")
    class ExtractUserIdTests {

        @Test
        @DisplayName("извлекает userId из subject")
        void extractUserId_ValidToken_ReturnsUserId() {
            String token = generateValidToken();

            Long result = jwtService.extractUserId(token);

            assertThat(result).isEqualTo(USER_ID);
        }
    }

    @Nested
    @DisplayName("extractEmail()")
    class ExtractEmailTests {

        @Test
        @DisplayName("извлекает email из claim")
        void extractEmail_ValidToken_ReturnsEmail() {
            String token = generateValidToken();

            String result = jwtService.extractEmail(token);

            assertThat(result).isEqualTo(EMAIL);
        }
    }

    @Nested
    @DisplayName("extractRolesFromToken()")
    class ExtractRolesTests {

        @Test
        @DisplayName("извлекает роли из claim")
        void extractRoles_ValidToken_ReturnsRoles() {
            String token = generateValidToken();

            List<String> result = jwtService.extractRolesFromToken(token);

            assertThat(result).containsExactlyInAnyOrder("ROLE_USER", "ROLE_ADMIN");
        }
    }

    @Nested
    @DisplayName("isTokenValid()")
    class IsTokenValidTests {

        @Test
        @DisplayName("валидный токен → true")
        void isTokenValid_ValidToken_ReturnsTrue() {
            String token = generateValidToken();

            assertThat(jwtService.isTokenValid(token)).isTrue();
        }

        @Test
        @DisplayName("истёкший токен → false")
        void isTokenValid_ExpiredToken_ReturnsFalse() {
            String token = generateExpiredToken();

            assertThat(jwtService.isTokenValid(token)).isFalse();
        }

        @Test
        @DisplayName("невалидная подпись → false")
        void isTokenValid_WrongSignature_ReturnsFalse() {
            SecretKey wrongKey = Keys.hmacShaKeyFor(
                    "wrong-secret-key-must-also-be-at-least-256-bits-long-for-hs256"
                            .getBytes(StandardCharsets.UTF_8));

            String token = Jwts.builder()
                    .subject("42")
                    .issuedAt(new Date())
                    .expiration(new Date(System.currentTimeMillis() + 3600000))
                    .signWith(wrongKey)
                    .compact();

            assertThat(jwtService.isTokenValid(token)).isFalse();
        }

        @Test
        @DisplayName("мусорная строка → false")
        void isTokenValid_GarbageString_ReturnsFalse() {
            assertThat(jwtService.isTokenValid("not.a.jwt")).isFalse();
        }

        @Test
        @DisplayName("null → false")
        void isTokenValid_Null_ReturnsFalse() {
            assertThat(jwtService.isTokenValid(null)).isFalse();
        }
    }

    @Nested
    @DisplayName("isTokenExpired()")
    class IsTokenExpiredTests {

        @Test
        @DisplayName("не истёкший токен → false")
        void isTokenExpired_ValidToken_ReturnsFalse() {
            String token = generateValidToken();

            assertThat(jwtService.isTokenExpired(token)).isFalse();
        }

        @Test
        @DisplayName("истёкший токен → true")
        void isTokenExpired_ExpiredToken_ReturnsTrue() {
            String token = generateExpiredToken();

            assertThat(jwtService.isTokenExpired(token)).isTrue();
        }
    }
}
