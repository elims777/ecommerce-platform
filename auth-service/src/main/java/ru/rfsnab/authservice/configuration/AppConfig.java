package ru.rfsnab.authservice.configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.client.RestTemplate;

import ru.rfsnab.authservice.service.OAuth2CodeService;
import ru.rfsnab.authservice.utils.JWTService;
import ru.rfsnab.authservice.utils.OAuth2LoginSuccessHandler;
import ru.rfsnab.authservice.utils.RoleExtractor;

import java.time.Clock;

@Configuration
public class AppConfig {
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public OAuth2LoginSuccessHandler oAuth2LoginSuccessHandler(
            RestTemplate restTemplate,
            JWTService jwtService,
            PasswordEncoder passwordEncoder,
            ObjectMapper objectMapper,
            RoleExtractor roleExtractor,
            OAuth2CodeService oAuth2CodeService) {
        return new OAuth2LoginSuccessHandler(restTemplate, jwtService, passwordEncoder, objectMapper, roleExtractor, oAuth2CodeService);
    }
}
