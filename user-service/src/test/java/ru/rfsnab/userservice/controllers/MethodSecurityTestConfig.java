package ru.rfsnab.userservice.controllers;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Тестовая Security-конфигурация для проверки @PreAuthorize на контроллерах.
 * Боевой SecurityConfig помечен @Profile("!test") и не поднимается в тестах вместе
 * с @EnableMethodSecurity, поэтому без этого конфига @PreAuthorize молча не срабатывает.
 * URL-правило — authenticated(), как в боевом конфиге: аноним получает 401 на уровне фильтра.
 */
@Profile("test")
@TestConfiguration
@EnableWebSecurity
@EnableMethodSecurity
public class MethodSecurityTestConfig {

    @Bean
    @Primary
    public SecurityFilterChain methodSecurityTestFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, authException) ->
                                response.setStatus(HttpStatus.UNAUTHORIZED.value()))
                        .accessDeniedHandler((request, response, accessDeniedException) ->
                                response.setStatus(HttpStatus.FORBIDDEN.value())));
        return http.build();
    }
}
