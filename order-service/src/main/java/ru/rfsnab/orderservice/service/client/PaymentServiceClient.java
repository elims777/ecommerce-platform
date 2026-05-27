package ru.rfsnab.orderservice.service.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import ru.rfsnab.orderservice.exception.ServiceUnavailableException;
import ru.rfsnab.orderservice.models.dto.payment.CreatePaymentClientRequest;
import ru.rfsnab.orderservice.models.dto.payment.PaymentLinkResponse;
import ru.rfsnab.orderservice.models.dto.payment.PaymentStatusResponse;
import ru.rfsnab.orderservice.models.entity.Order;
import ru.rfsnab.orderservice.models.entity.enums.PaymentMethod;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
public class PaymentServiceClient {

    private final RestTemplate restTemplate;
    private final String paymentServiceUrl;
    private final String internalSecret;

    public PaymentServiceClient(RestTemplate restTemplate,
                                @Value("${services.payment.url}") String paymentServiceUrl,
                                @Value("${internal.secret}") String internalSecret) {
        this.restTemplate = restTemplate;
        this.paymentServiceUrl = paymentServiceUrl;
        this.internalSecret = internalSecret;
    }

    public PaymentLinkResponse createPayment(Order order) {
        if (order.getPaymentMethod() == PaymentMethod.CASH_ON_DELIVERY) {
            throw new IllegalStateException("createPayment must not be called for CASH_ON_DELIVERY");
        }
        try {
            var body = CreatePaymentClientRequest.from(order);
            return restTemplate.exchange(
                    paymentServiceUrl + "/api/v1/payments",
                    HttpMethod.POST,
                    new HttpEntity<>(body, internalHeaders()),
                    PaymentLinkResponse.class
            ).getBody();
        } catch (Exception e) {
            log.error("Payment service unavailable: {}", e.getMessage());
            throw new ServiceUnavailableException("Payment service unavailable");
        }
    }

    public PaymentStatusResponse getPaymentStatus(UUID orderId) {
        try {
            return restTemplate.exchange(
                    paymentServiceUrl + "/api/v1/payments/{orderId}/status",
                    HttpMethod.GET,
                    new HttpEntity<>(internalHeaders()),
                    PaymentStatusResponse.class,
                    orderId
            ).getBody();
        } catch (Exception e) {
            log.error("Payment service unavailable: {}", e.getMessage());
            throw new ServiceUnavailableException("Payment service unavailable");
        }
    }

    public void recordCashPayment(UUID orderId, BigDecimal amount) {
        try {
            var body = Map.of("amount", amount);
            restTemplate.exchange(
                    paymentServiceUrl + "/api/v1/payments/{orderId}/cash",
                    HttpMethod.POST,
                    new HttpEntity<>(body, internalHeaders()),
                    Void.class,
                    orderId
            );
        } catch (Exception e) {
            log.error("Payment service unavailable: {}", e.getMessage());
            throw new ServiceUnavailableException("Payment service unavailable");
        }
    }

    public void refundPayment(UUID orderId) {
        try {
            restTemplate.exchange(
                    paymentServiceUrl + "/api/v1/payments/{orderId}/refund",
                    HttpMethod.POST,
                    new HttpEntity<>(internalHeaders()),
                    Void.class,
                    orderId
            );
        } catch (Exception e) {
            log.error("Payment service unavailable: {}", e.getMessage());
            throw new ServiceUnavailableException("Payment service unavailable");
        }
    }

    private HttpHeaders internalHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Internal-Token", internalSecret);
        headers.set("Content-Type", "application/json");
        return headers;
    }
}
