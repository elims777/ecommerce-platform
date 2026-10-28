package ru.rfsnab.integrationservice.config;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
@RequiredArgsConstructor
public class WebClientConfig {

    private static final String INTERNAL_TOKEN_HEADER = "X-Internal-Token";

    private final IntegrationProperties properties;

    @Bean
    public WebClient productServiceClient(@Value("${internal.secret}") String internalSecret) {
        return WebClient.builder()
                .baseUrl(properties.getProductService().getUrl())
                .defaultHeader(INTERNAL_TOKEN_HEADER, internalSecret)
                .clientConnector(new ReactorClientHttpConnector())
                .build();
    }

    @Bean
    public WebClient orderServiceClient(@Value("${internal.secret}") String internalSecret) {
        return WebClient.builder()
                .baseUrl(properties.getOrderService().getUrl())
                .defaultHeader(INTERNAL_TOKEN_HEADER, internalSecret)
                .clientConnector(new ReactorClientHttpConnector())
                .build();
    }

    @Bean
    public RestTemplate productServiceRestTemplate(
            @Value("${internal.secret}") String internalSecret,
            @Value("${integration.product-service.read-timeout-ms:60000}") int readTimeoutMs) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(readTimeoutMs);

        RestTemplate template = new RestTemplate(factory);
        template.getInterceptors().add((request, body, execution) -> {
            request.getHeaders().set(INTERNAL_TOKEN_HEADER, internalSecret);
            return execution.execute(request, body);
        });
        return template;
    }
}