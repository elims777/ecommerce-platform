package ru.rfsnab.paymentservice.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import ru.rfsnab.paymentservice.models.dto.tochka.TochkaCreateRequest;
import ru.rfsnab.paymentservice.models.dto.tochka.TochkaCreateResponse;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TochkaApiClientTest {

    @Mock WebClient webClient;
    @Mock WebClient.RequestBodyUriSpec requestBodyUriSpec;
    @Mock WebClient.RequestBodySpec requestBodySpec;
    @Mock WebClient.ResponseSpec responseSpec;
    @Mock WebClient.RequestHeadersUriSpec requestHeadersUriSpec;
    @Mock WebClient.RequestHeadersSpec requestHeadersSpec;

    TochkaApiClient client;

    @BeforeEach
    void setUp() {
        client = new TochkaApiClient(webClient);
    }

    @Test
    void createPayment_returnsResponse() {
        var request = new TochkaCreateRequest(
                "CUST001", BigDecimal.valueOf(1000), "Заказ #ORD-00001",
                List.of("card", "sbp"),
                "https://rfsnab.ru/payment/success?orderId=abc",
                "https://rfsnab.ru/payment/fail?orderId=abc",
                "abc"
        );
        var expected = new TochkaCreateResponse("op-123", "https://tochka.pay/abc");

        when(webClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri("/payments")).thenReturn(requestBodySpec);
        when(requestBodySpec.bodyValue(any())).thenReturn(requestBodySpec);
        when(requestBodySpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(TochkaCreateResponse.class)).thenReturn(Mono.just(expected));

        TochkaCreateResponse result = client.createPayment(request);

        assertThat(result.operationId()).isEqualTo("op-123");
        assertThat(result.paymentLink()).isEqualTo("https://tochka.pay/abc");
    }
}
