package ru.rfsnab.userservice.configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import ru.rfsnab.userservice.models.dto.ErrorResponse;

import java.time.LocalDateTime;

@Configuration
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
                        .requestMatchers(
                                "/v1/users/signup",
                                "/v1/users/authenticate",
                                "/v1/users/oauth2-login",
                                "/actuator/health",
                                "/v1/users/*/verify",
                                "/swagger-ui/**",
                                "/api-docs/**",
                                "/swagger-ui.html"
                        ).permitAll()
                        .anyRequest().authenticated()
                )
                .exceptionHandling(exceptions -> exceptions
                        // Когда пользователь не аутентифицирован (нет токена или токен невалидный)
                        .authenticationEntryPoint(
                                (request,
                                 response,
                                 authException) -> {
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
                            response.getWriter().write(mapper.writeValueAsString(errorResponse));
                        })
                        // Когда пользователь аутентифицирован, но не имеет прав доступа
                        .accessDeniedHandler(
                                (request,
                                 response,
                                 accessDeniedException) -> {
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
