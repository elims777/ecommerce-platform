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
    private final KafkaTopicsProperties topics;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    private void send(String eventType, Order order){
        OrderEvent event = OrderEvent.builder()
                .eventType(eventType)
                .orderId(order.getId())
                .orderNumber(order.getOrderNumber())
                .userId(order.getUserId())
                .status(order.getStatus().getDisplayName())
                .totalAmount(order.getTotalAmount())
                .customerEmail(order.getCustomerEmail())
                .customerName(order.getCustomerName())
                .customerPhone(order.getCustomerPhone())
                .timestamp(LocalDateTime.now())
                .items(order.getItems().stream()
                        .map(i -> new OrderEvent.OrderItemLine(i.getProductName(), i.getQuantity(), i.getPrice(), i.getVariantAttributes()))
                        .toList())
                .deliveryMethod(order.getDeliveryMethod() != null ? order.getDeliveryMethod().name() : null)
                .paymentMethod(order.getPaymentMethod() != null ? order.getPaymentMethod().name() : null)
                .comment(order.getComment())
                .deliveryAddress(order.getDeliveryAddress() != null
                        ? new OrderEvent.DeliveryAddressDto(
                                order.getDeliveryAddress().getCity(),
                                order.getDeliveryAddress().getStreet(),
                                order.getDeliveryAddress().getBuilding(),
                                order.getDeliveryAddress().getApartment(),
                                order.getDeliveryAddress().getPostalCode(),
                                order.getDeliveryAddress().getPhone(),
                                order.getDeliveryAddress().getRecipientName())
                        : null)
                .customerType(order.getCustomerType() != null ? order.getCustomerType().name() : null)
                .companyName(order.getCompanyName())
                .inn(order.getInn())
                .build();

        kafkaTemplate.send(topics.getOrderEvents(), order.getId().toString(), event);
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
