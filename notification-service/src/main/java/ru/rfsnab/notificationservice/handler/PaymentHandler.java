package ru.rfsnab.notificationservice.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import ru.rfsnab.notificationservice.models.PaymentEvent;
import ru.rfsnab.notificationservice.service.EmailService;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentHandler implements NotificationHandler {

    private final ObjectMapper objectMapper;
    private final EmailService emailService;

    @Value("${app.kafka.topic.payment-events}")
    private String topic;

    @Override
    public boolean supports(String topic, String eventType) {
        return this.topic.equals(topic) && "PAYMENT_STATUS_CHANGED".equals(eventType);
    }

    @Override
    public void handle(String eventJson) {
        try {
            PaymentEvent event = objectMapper.readValue(eventJson, PaymentEvent.class);
            if (event.customerEmail() == null || event.customerEmail().isBlank()) {
                log.warn("PaymentEvent has no customerEmail, skipping notification: orderId={}", event.orderId());
                return;
            }

            switch (event.status()) {
                case "APPROVED" -> {
                    emailService.sendPaymentApprovedEmail(event.customerEmail(), event.amount(), event.paymentMode());
                    log.info("Payment approved email sent: orderId={}", event.orderId());
                }
                case "FAILED" -> {
                    emailService.sendPaymentFailedEmail(event.customerEmail());
                    log.info("Payment failed email sent: orderId={}", event.orderId());
                }
                default -> log.warn("Unhandled payment status in notification: {}", event.status());
            }
        } catch (Exception e) {
            log.error("Failed to handle payment event: {}", eventJson, e);
        }
    }
}
