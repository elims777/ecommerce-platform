package ru.rfsnab.paymentservice.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Slf4j
@Component
public class InternalTokenFilter extends OncePerRequestFilter {

    private final String internalSecret;

    public InternalTokenFilter(@Value("${internal.secret}") String internalSecret) {
        this.internalSecret = internalSecret;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String token = request.getHeader("X-Internal-Token");
        if (!internalSecret.equals(token)) {
            log.warn("Rejected request to {} — missing or invalid X-Internal-Token", request.getRequestURI());
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Forbidden");
            return;
        }
        filterChain.doFilter(request, response);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/actuator") || path.startsWith("/api-docs") || path.startsWith("/swagger-ui");
    }
}
