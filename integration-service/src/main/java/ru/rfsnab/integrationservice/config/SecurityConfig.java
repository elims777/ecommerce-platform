package ru.rfsnab.integrationservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
@Profile("!test")
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        // 1С обмен — своя авторизация через cookie
                        .requestMatchers("/1c-exchange/**").permitAll()
                        // Логи обменов — для admin-панели (авторизация на стороне gateway)
                        .requestMatchers("/1c-exchange/logs").permitAll()
                        // FTK импорт — авторизация на стороне gateway (ROLE_ADMIN)
                        .requestMatchers("/api/v1/integration/**").permitAll()
                        // Swagger — только для dev
                        .requestMatchers("/swagger-ui/**", "/api-docs/**").permitAll()
                        .anyRequest().denyAll()
                );
        return http.build();
    }

}
