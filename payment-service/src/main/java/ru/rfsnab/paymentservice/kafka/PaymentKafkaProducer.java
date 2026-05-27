package ru.rfsnab.paymentservice.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import ru.rfsnab.paymentservice.config.KafkaConfig;
import ru.rfsnab.paymentservice.models.dto.event.PaymentEvent;
import ru.rfsnab.paymentservice.models.entity.Payment;
import ru.rfsnab.paymentservice.models.entity.enums.PaymentStatus;

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentKafkaProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void sendPaymentEvent(Payment payment, PaymentStatus status) {
        PaymentEvent event = new PaymentEvent(
                "PAYMENT_STATUS_CHANGED",
                payment.getOrderId(),
                status,
                payment.getAmount(),
                payment.getPaymentMode(),
                payment.getCustomerEmail(),
                LocalDateTime.now()
        );
        kafkaTemplate.send(KafkaConfig.PAYMENT_PROCESSED_TOPIC, payment.getOrderId().toString(), event);
        log.info("PaymentEvent sent: orderId={}, status={}", payment.getOrderId(), status);
    }
}
