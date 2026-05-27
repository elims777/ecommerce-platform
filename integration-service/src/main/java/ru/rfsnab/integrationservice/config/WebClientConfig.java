package ru.rfsnab.integrationservice.config;

import io.netty.resolver.DefaultAddressResolverGroup;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;

@Configuration
@RequiredArgsConstructor
public class WebClientConfig {

    private final IntegrationProperties properties;

    @Bean
    public WebClient productServiceClient() {
        return WebClient.builder()
                .baseUrl(properties.getProductService().getUrl())
                .clientConnector(new ReactorClientHttpConnector())
                .build();
    }

    @Bean
    public WebClient orderServiceClient() {
        return WebClient.builder()
                .baseUrl(properties.getOrderService().getUrl())
                .clientConnector(new ReactorClientHttpConnector())
                .build();
    }

    private HttpClient buildHttpClient() {
        return HttpClient.newConnection()
                .resolver(DefaultAddressResolverGroup.INSTANCE)
                .responseTimeout(Duration.ofSeconds(30));
    }

    @Bean
    public RestTemplate productServiceRestTemplate() {
        return new RestTemplate();
    }
}