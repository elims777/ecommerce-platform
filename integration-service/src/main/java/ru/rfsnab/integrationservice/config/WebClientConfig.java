package ru.rfsnab.integrationservice.config;

import io.netty.resolver.DefaultAddressResolverGroup;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
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
    public WebClient productServiceClient(@Value("${internal.secret}") String internalSecret) {
        return WebClient.builder()
                .baseUrl(properties.getProductService().getUrl())
                .defaultHeader("X-Internal-Token", internalSecret)
                .clientConnector(new ReactorClientHttpConnector())
                .build();
    }

    @Bean
    public WebClient orderServiceClient(@Value("${internal.secret}") String internalSecret) {
        return WebClient.builder()
                .baseUrl(properties.getOrderService().getUrl())
                .defaultHeader("X-Internal-Token", internalSecret)
                .clientConnector(new ReactorClientHttpConnector())
                .build();
    }

    private HttpClient buildHttpClient() {
        return HttpClient.newConnection()
                .resolver(DefaultAddressResolverGroup.INSTANCE)
                .responseTimeout(Duration.ofSeconds(30));
    }

    @Bean
    public RestTemplate productServiceRestTemplate(@Value("${internal.secret}") String internalSecret) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(10000);

        RestTemplate template = new RestTemplate(factory);
        template.getInterceptors().add((request, body, execution) -> {
            request.getHeaders().set("X-Internal-Token", internalSecret);
            return execution.execute(request, body);
        });
        return template;
    }
}