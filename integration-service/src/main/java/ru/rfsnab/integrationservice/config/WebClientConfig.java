package ru.rfsnab.integrationservice.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
@RequiredArgsConstructor
public class WebClientConfig {

    private final IntegrationProperties properties;

    @Bean
    public WebClient productServiceClient() {
        return WebClient.builder()
                .baseUrl(properties.getProductService().getUrl())
                .build();
    }

    @Bean
    public WebClient orderServiceClient() {
        return WebClient.builder()
                .baseUrl(properties.getOrderService().getUrl())
                .build();
    }
}
