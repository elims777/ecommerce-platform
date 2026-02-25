package ru.rfsnab.orderservice.kafka;

import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import ru.rfsnab.orderservice.models.dto.event.OrderEvent;
import ru.rfsnab.orderservice.models.entity.Order;

import java.time.LocalDateTime;

/**
 * Отправка Kafka events для заказов.
 */
@Component
@RequiredArgsConstructor
public class OrderKafkaProducer {
    private static final String TOPIC_ORDERS = "order-events";
    private final KafkaTemplate<String, OrderEvent> kafkaTemplate;

    private void send(String eventType, Order order){
        OrderEvent event = OrderEvent.builder()
                .eventType(eventType)
                .orderId(order.getId())
                .orderNumber(order.getOrderNumber())
                .userId(order.getUserId())
                .status(order.getStatus().getDisplayName())
                .totalAmount(order.getTotalAmount())
                .customerEmail(order.getCustomerEmail())
                .timestamp(LocalDateTime.now())
                .build();

        kafkaTemplate.send(TOPIC_ORDERS, order.getId().toString(), event);
    }

    public void sendOrderCreated(Order order) {
        send("ORDER_CREATED", order);
    }

    public void sendOrderPaid(Order order) {
        send("ORDER_PAID", order);
    }

    public void sendOrderCancelled(Order order) {
        send("ORDER_CANCELLED", order);
    }

    public void sendOrderStatusChanged(Order order) {
        send("ORDER_STATUS_CHANGED", order);
    }
}
