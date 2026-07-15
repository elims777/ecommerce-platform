package ru.rfsnab.userservice.configuration;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;

/**
 * Проверяет X-Internal-Token на путях service-to-service (order-service).
 * Прочие пути пропускает без вмешательства — их обработает JwtAuthenticationFilter.
 * Валидный токен ставит ROLE_INTERNAL в SecurityContext; невалидный — просто не ставит,
 * 403 отдаст authorize-цепочка через hasRole('INTERNAL').
 * Сравнение через SHA-256(token) — длины всегда 32 байта, MessageDigest.isEqual constant-time.
 */
@Slf4j
@Component
public class InternalTokenFilter extends OncePerRequestFilter {

    private static final List<String> INTERNAL_PATHS = List.of(
            "/v1/users/*/profile-completeness"
    );

    private final AntPathMatcher matcher = new AntPathMatcher();
    private final byte[] expectedTokenHash;

    public InternalTokenFilter(@Value("${internal.secret}") String internalSecret) {
        this.expectedTokenHash = sha256(internalSecret.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String token = request.getHeader("X-Internal-Token");
        if (token != null) {
            byte[] tokenHash = sha256(token.getBytes(StandardCharsets.UTF_8));
            if (MessageDigest.isEqual(tokenHash, expectedTokenHash)) {
                var auth = new UsernamePasswordAuthenticationToken(
                        "internal-service", null,
                        List.of(new SimpleGrantedAuthority("ROLE_INTERNAL")));
                SecurityContextHolder.getContext().setAuthentication(auth);
            } else {
                log.warn("Invalid X-Internal-Token on {}", request.getRequestURI());
            }
        }
        filterChain.doFilter(request, response);
    }

    private static byte[] sha256(byte[] input) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(input);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 недоступен в JVM", e);
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return INTERNAL_PATHS.stream().noneMatch(p -> matcher.match(p, path));
    }
}
