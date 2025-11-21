package ru.rfsnab.userservice.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/v1/users/signup",
                                "/api/v1/auth/register",
                                "/v1/users/oauth2-login",
                                "/api/v1/users/check/**",
                                "/v1/users/by-email",
                                "/v1/users/authenticate")
                        .permitAll()
                        .anyRequest().authenticated());
        return http.build();
    }
}
