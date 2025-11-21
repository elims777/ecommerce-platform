package ru.rfsnab.authservice.configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.client.RestTemplate;
import ru.rfsnab.authservice.utils.JWTService;
import ru.rfsnab.authservice.utils.OAuth2LoginSuccessHandler;

@Configuration
public class AppConfig {
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
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
            ObjectMapper objectMapper) {
        return new OAuth2LoginSuccessHandler(restTemplate, jwtService, passwordEncoder, objectMapper);
    }
}
