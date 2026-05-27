package ru.rfsnab.paymentservice.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
@RequiredArgsConstructor
public class WebClientConfig {

    private final TochkaProperties tochkaProperties;

    @Bean
    public WebClient tochkaWebClient() {
        return WebClient.builder()
                .baseUrl(tochkaProperties.getBaseUrl())
                .defaultHeader("Authorization", "Bearer " + tochkaProperties.getToken())
                .defaultHeader("Content-Type", "application/json")
                .build();
    }
}
