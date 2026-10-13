package ru.rfsnab.orderservice.config;

import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

import java.time.Duration;

/**
 * Таймауты HTTP-клиентов к API транспортных компаний: connect 5 с, read 30 с.
 */
@Configuration
public class CarrierHttpConfig {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(30);

    /** Применяется ко всем RestClient, собранным из Spring-овского RestClient.Builder. */
    @Bean
    public RestClientCustomizer carrierRestClientTimeouts() {
        return builder -> {
            SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
            factory.setConnectTimeout(CONNECT_TIMEOUT);
            factory.setReadTimeout(READ_TIMEOUT);
            builder.requestFactory(factory);
        };
    }
}
