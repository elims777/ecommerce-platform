package ru.rfsnab.productservice.configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import ru.rfsnab.productservice.dto.ErrorResponse;
import ru.rfsnab.productservice.security.JwtAuthenticationFilter;

import java.time.LocalDateTime;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {
    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(AbstractHttpConfigurer::disable)
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Публичные endpoints (только чтение)
                        .requestMatchers(HttpMethod.GET, "/api/v1/categories/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/products/**").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/info").permitAll()

                        // Управление категориями - только ADMIN и MANAGER
                        .requestMatchers(HttpMethod.POST, "/api/v1/categories/**").hasAnyAuthority("ROLE_ADMIN", "ROLE_MANAGER")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/categories/**").hasAnyAuthority("ROLE_ADMIN", "ROLE_MANAGER")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/categories/**").hasAnyAuthority("ROLE_ADMIN", "ROLE_MANAGER")

                        // Управление товарами - только ADMIN и MANAGER
                        .requestMatchers(HttpMethod.POST, "/api/v1/products/**").hasAnyAuthority("ROLE_ADMIN", "ROLE_MANAGER")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/products/**").hasAnyAuthority("ROLE_ADMIN", "ROLE_MANAGER")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/products/**").hasAnyAuthority("ROLE_ADMIN", "ROLE_MANAGER")

                        // Все остальные запросы требуют аутентификации
                        .anyRequest().authenticated()
                )
                .exceptionHandling(exceptions -> exceptions
                        // Когда пользователь не аутентифицирован (нет токена или токен невалидный)
                        .authenticationEntryPoint((request, response, authException) -> {
                            response.setStatus(HttpStatus.UNAUTHORIZED.value());
                            response.setContentType("application/json;charset=UTF-8");

                            ErrorResponse errorResponse = ErrorResponse.builder()
                                    .timestamp(LocalDateTime.now())
                                    .status(HttpStatus.UNAUTHORIZED.value())
                                    .error("Unauthorized")
                                    .message("Требуется аутентификация. Пожалуйста, войдите в систему")
                                    .path(request.getRequestURI())
                                    .build();

                            ObjectMapper mapper = new ObjectMapper();
                            mapper.registerModule(new JavaTimeModule());
                            mapper.disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
                            response.getWriter().write(mapper.writeValueAsString(errorResponse));
                        })
                        // Когда пользователь аутентифицирован, но не имеет прав доступа
                        .accessDeniedHandler((request, response, accessDeniedException) -> {
                            response.setStatus(HttpStatus.FORBIDDEN.value());
                            response.setContentType("application/json;charset=UTF-8");

                            ErrorResponse errorResponse = ErrorResponse.builder()
                                    .timestamp(LocalDateTime.now())
                                    .status(HttpStatus.FORBIDDEN.value())
                                    .error("Forbidden")
                                    .message("Недостаточно прав для выполнения операции")
                                    .path(request.getRequestURI())
                                    .build();

                            ObjectMapper mapper = new ObjectMapper();
                            mapper.registerModule(new JavaTimeModule());
                            response.getWriter().write(mapper.writeValueAsString(errorResponse));
                        })
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
