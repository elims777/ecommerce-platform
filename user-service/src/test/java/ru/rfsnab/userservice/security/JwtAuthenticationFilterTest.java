package ru.rfsnab.userservice.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("JwtAuthenticationFilter Unit Tests")
class JwtAuthenticationFilterTest {

    private static final String SECRET = "test-secret-key-for-testing-must-be-at-least-256-bits-long-for-hs256";

    private JwtAuthenticationFilter filter;

    @Mock
    private FilterChain filterChain;

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        JWTProperties properties = new JWTProperties(SECRET);
        JWTService jwtService = new JWTService(properties);
        filter = new JwtAuthenticationFilter(jwtService);

        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private String generateValidToken(Long userId, List<String> roles) {
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        return Jwts.builder()
                .subject(userId.toString())
                .claim("email", "test@example.com")
                .claim("roles", roles)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 3600000))
                .signWith(key)
                .compact();
    }

    private String generateExpiredToken() {
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        return Jwts.builder()
                .subject("42")
                .claim("email", "test@example.com")
                .claim("roles", List.of("ROLE_USER"))
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() - 1000))
                .signWith(key)
                .compact();
    }

    @Nested
    @DisplayName("Запрос без Authorization")
    class NoAuthHeaderTests {

        @Test
        @DisplayName("нет заголовка → filterChain вызван, context пустой")
        void noAuthHeader_ContinuesFilterChain() throws ServletException, IOException {
            filter.doFilterInternal(request, response, filterChain);

            verify(filterChain).doFilter(request, response);
            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        }

        @Test
        @DisplayName("заголовок без Bearer → filterChain вызван, context пустой")
        void nonBearerHeader_ContinuesFilterChain() throws ServletException, IOException {
            request.addHeader("Authorization", "Basic abc123");

            filter.doFilterInternal(request, response, filterChain);

            verify(filterChain).doFilter(request, response);
            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        }
    }

    @Nested
    @DisplayName("Валидный JWT")
    class ValidTokenTests {

        @Test
        @DisplayName("устанавливает Authentication в SecurityContext")
        void validToken_SetsAuthentication() throws ServletException, IOException {
            String token = generateValidToken(42L, List.of("ROLE_USER"));
            request.addHeader("Authorization", "Bearer " + token);

            filter.doFilterInternal(request, response, filterChain);

            verify(filterChain).doFilter(request, response);
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            assertThat(auth).isNotNull();
            assertThat(auth.getPrincipal()).isEqualTo("42");
        }

        @Test
        @DisplayName("сохраняет роли в authorities")
        void validToken_SetsCorrectAuthorities() throws ServletException, IOException {
            String token = generateValidToken(42L, List.of("ROLE_USER", "ROLE_ADMIN"));
            request.addHeader("Authorization", "Bearer " + token);

            filter.doFilterInternal(request, response, filterChain);

            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            assertThat(auth.getAuthorities())
                    .extracting(Object::toString)
                    .containsExactlyInAnyOrder("ROLE_USER", "ROLE_ADMIN");
        }

        @Test
        @DisplayName("не перезаписывает существующий Authentication")
        void validToken_DoesNotOverwriteExistingAuth() throws ServletException, IOException {
            // Предустановленная аутентификация
            var existingAuth = new org.springframework.security.authentication
                    .UsernamePasswordAuthenticationToken("existing", null, List.of());
            SecurityContextHolder.getContext().setAuthentication(existingAuth);

            String token = generateValidToken(42L, List.of("ROLE_USER"));
            request.addHeader("Authorization", "Bearer " + token);

            filter.doFilterInternal(request, response, filterChain);

            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            assertThat(auth.getPrincipal()).isEqualTo("existing");
        }
    }

    @Nested
    @DisplayName("Невалидный JWT")
    class InvalidTokenTests {

        @Test
        @DisplayName("истёкший токен → context пустой, filterChain вызван")
        void expiredToken_NoAuthentication() throws ServletException, IOException {
            String token = generateExpiredToken();
            request.addHeader("Authorization", "Bearer " + token);

            filter.doFilterInternal(request, response, filterChain);

            verify(filterChain).doFilter(request, response);
            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        }

        @Test
        @DisplayName("мусорный токен → context пустой, filterChain вызван")
        void garbageToken_NoAuthentication() throws ServletException, IOException {
            request.addHeader("Authorization", "Bearer not.a.valid.jwt");

            filter.doFilterInternal(request, response, filterChain);

            verify(filterChain).doFilter(request, response);
            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        }

        @Test
        @DisplayName("токен с неверной подписью → context пустой")
        void wrongSignature_NoAuthentication() throws ServletException, IOException {
            SecretKey wrongKey = Keys.hmacShaKeyFor(
                    "wrong-secret-key-must-also-be-at-least-256-bits-long-for-hs256"
                            .getBytes(StandardCharsets.UTF_8));

            String token = Jwts.builder()
                    .subject("42")
                    .claim("roles", List.of("ROLE_USER"))
                    .issuedAt(new Date())
                    .expiration(new Date(System.currentTimeMillis() + 3600000))
                    .signWith(wrongKey)
                    .compact();

            request.addHeader("Authorization", "Bearer " + token);

            filter.doFilterInternal(request, response, filterChain);

            verify(filterChain).doFilter(request, response);
            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        }
    }
}