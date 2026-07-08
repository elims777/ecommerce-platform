package ru.rfsnab.orderservice.kafka;

import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import ru.rfsnab.orderservice.models.dto.event.OrderEvent;
import ru.rfsnab.orderservice.models.entity.Order;
import ru.rfsnab.orderservice.models.entity.WarehousePoint;
import ru.rfsnab.orderservice.repository.WarehousePointRepository;

import java.time.LocalDateTime;

/**
 * Отправка Kafka events для заказов.
 */
@Component
@RequiredArgsConstructor
public class OrderKafkaProducer {
    private final KafkaTopicsProperties topics;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final WarehousePointRepository warehousePointRepository;

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
                .pickupPoint(buildPickupPoint(order))
                .build();

        kafkaTemplate.send(topics.getOrderEvents(), order.getId().toString(), event);
    }

    private OrderEvent.PickupPointDto buildPickupPoint(Order order) {
        if (order.getWarehousePointId() == null) {
            return null;
        }
        WarehousePoint point = warehousePointRepository.findById(order.getWarehousePointId()).orElse(null);
        if (point == null) {
            return null;
        }
        return new OrderEvent.PickupPointDto(point.getName(), point.getCity(), point.getStreet(),
                point.getBuilding(), point.getPostalCode(), point.getPhoneNumber(), point.getWorkingHours());
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
