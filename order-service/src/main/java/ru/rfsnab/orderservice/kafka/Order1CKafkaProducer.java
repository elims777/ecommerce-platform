package ru.rfsnab.orderservice.kafka;

import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import ru.rfsnab.orderservice.models.dto.event.Order1CExportEvent;
import ru.rfsnab.orderservice.models.entity.DeliveryAddress;
import ru.rfsnab.orderservice.models.entity.Order;

import java.util.List;

/**
 * Отправка полного заказа в Kafka для выгрузки в 1С.
 * Топик "order-1c-export" — отдельный от "order-events" (уведомления).
 */
@Component
@RequiredArgsConstructor
public class Order1CKafkaProducer {

    private final KafkaTopicsProperties topics;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    /**
     * Отправляет полные данные заказа для 1С.
     * Вызывается из OrderService.createOrder() после сохранения.
     */
    public void sendOrderFor1C(Order order) {
        DeliveryAddress address = order.getDeliveryAddress();
        boolean isPickup = address == null;

        Order1CExportEvent event = Order1CExportEvent.builder()
                .orderId(order.getId())
                .orderNumber(order.getOrderNumber())
                .createdAt(order.getCreatedAt())
                .userId(order.getUserId())
                .customerEmail(order.getCustomerEmail())
                .recipientName(isPickup ? order.getPickupRecipientName() : address.getRecipientName())
                .recipientPhone(isPickup ? order.getPickupRecipientPhone() : address.getPhone())
                .deliveryMethod(order.getDeliveryMethod().name())
                .city(isPickup ? null : address.getCity())
                .street(isPickup ? null : address.getStreet())
                .building(isPickup ? null : address.getBuilding())
                .apartment(isPickup ? null : address.getApartment())
                .postalCode(isPickup ? null : address.getPostalCode())
                .warehousePointId(order.getWarehousePointId())
                .paymentMethod(order.getPaymentMethod().name())
                .totalAmount(order.getTotalAmount())
                .comment(order.getComment())
                .items(mapItems(order))
                .build();

        kafkaTemplate.send(topics.getOrder1cExport(), order.getId().toString(), event)
                .whenComplete((result, ex) -> {
            if (ex == null) {
                System.out.println(">>> SUCCESS: sent to " + topics.getOrder1cExport()
                        + " offset=" + result.getRecordMetadata().offset());
            } else {
                System.out.println(">>> FAILED: " + ex.getMessage());
                ex.printStackTrace();
            }
        });
    }

    private List<Order1CExportEvent.ExportOrderItem> mapItems(Order order){
        return order.getItems().stream()
                .map(item -> Order1CExportEvent.ExportOrderItem.builder()
                        .productId(item.getProductId())
                        .externalId(item.getExternalId())
                        .productName(item.getProductName())
                        .quantity(item.getQuantity())
                        .price(item.getPrice())
                        .build())
                .toList();
    }
}
