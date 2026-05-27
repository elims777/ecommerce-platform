package ru.rfsnab.orderservice.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import ru.rfsnab.orderservice.models.dto.event.PaymentEvent;
import ru.rfsnab.orderservice.models.entity.Order;
import ru.rfsnab.orderservice.models.entity.enums.OrderStatus;
import ru.rfsnab.orderservice.repository.OrderRepository;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentEventConsumer {

    private final OrderRepository orderRepository;
    private final ObjectMapper objectMapper;
    private final OrderKafkaProducer kafkaProducer;

    @KafkaListener(
            topics = "${app.kafka.topics.payment-processed}",
            groupId = "${spring.kafka.consumer.group-id}"
    )
    public void onPaymentEvent(ConsumerRecord<String, String> record) {
        try {
            PaymentEvent event = objectMapper.readValue(record.value(), PaymentEvent.class);
            log.info("PaymentEvent received: orderId={}, status={}", event.orderId(), event.status());
            processPaymentEvent(event);
        } catch (Exception e) {
            log.error("Failed to process payment event: {}", record.value(), e);
        }
    }

    private void processPaymentEvent(PaymentEvent event) {
        Order order = orderRepository.findById(event.orderId()).orElse(null);
        if (order == null) {
            log.warn("Order not found for payment event: orderId={}", event.orderId());
            return;
        }

        OrderStatus newStatus = switch (event.status()) {
            case "APPROVED" -> OrderStatus.PAID;
            case "FAILED"   -> OrderStatus.PAYMENT_FAILED;
            case "REFUNDED" -> OrderStatus.REFUNDED;
            default -> {
                log.warn("Unknown payment status: {}", event.status());
                yield null;
            }
        };

        if (newStatus == null) {
            return;
        }

        order.setStatus(newStatus);
        orderRepository.save(order);
        kafkaProducer.sendOrderStatusChanged(order);
        log.info("Order status updated from payment event: orderId={}, newStatus={}", event.orderId(), newStatus);
    }
}
