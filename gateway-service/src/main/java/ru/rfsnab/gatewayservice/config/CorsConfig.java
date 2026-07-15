package ru.rfsnab.gatewayservice.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * CORS конфигурация для gateway.
 * Единая точка настройки — сервисам за gateway свой CORS не нужен.
 */
@Configuration
public class CorsConfig {

    private final List<String> allowedOrigins;

    public CorsConfig(
            @Value("${cors.allowed-origins:http://localhost:3000,http://localhost:5173,http://localhost:8080}")
            List<String> allowedOrigins) {
        this.allowedOrigins = allowedOrigins;
    }

    @Bean
    public CorsWebFilter corsWebFilter() {
        CorsConfiguration config = new CorsConfiguration();

        // Разрешённые origins — задаются через cors.allowed-origins (дефолт: localhost для дева)
        config.setAllowedOriginPatterns(allowedOrigins);

        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L); // Кэш preflight на 1 час

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);

        return new CorsWebFilter(source);
    }
}
