package ru.rfsnab.paymentservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import ru.rfsnab.paymentservice.exception.TochkaApiException;
import ru.rfsnab.paymentservice.models.dto.tochka.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class TochkaApiClient {

    private final WebClient tochkaWebClient;

    public TochkaCreateResponse createPayment(TochkaCreateRequest request) {
        try {
            return tochkaWebClient.post()
                    .uri("/payments")
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(TochkaCreateResponse.class)
                    .block();
        } catch (WebClientResponseException e) {
            log.error("Tochka create payment error: status={}, body={}", e.getStatusCode(), e.getResponseBodyAsString());
            if (e.getStatusCode().is4xxClientError()) {
                throw new TochkaApiException("Tochka API: " + e.getResponseBodyAsString(), HttpStatus.BAD_REQUEST);
            }
            throw new TochkaApiException("Tochka API unavailable", HttpStatus.SERVICE_UNAVAILABLE);
        }
    }

    public TochkaStatusResponse getPaymentStatus(String operationId) {
        try {
            return tochkaWebClient.get()
                    .uri("/payments/{operationId}", operationId)
                    .retrieve()
                    .bodyToMono(TochkaStatusResponse.class)
                    .block();
        } catch (WebClientResponseException e) {
            log.error("Tochka get status error: operationId={}, status={}", operationId, e.getStatusCode());
            throw new TochkaApiException("Tochka API unavailable", HttpStatus.SERVICE_UNAVAILABLE);
        }
    }

    public void refundPayment(String operationId, TochkaRefundRequest request) {
        try {
            tochkaWebClient.post()
                    .uri("/payments/{operationId}/refund", operationId)
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(Void.class)
                    .block();
        } catch (WebClientResponseException e) {
            log.error("Tochka refund error: operationId={}, status={}", operationId, e.getStatusCode());
            throw new TochkaApiException("Tochka refund failed: " + e.getResponseBodyAsString(), HttpStatus.BAD_REQUEST);
        }
    }
}
