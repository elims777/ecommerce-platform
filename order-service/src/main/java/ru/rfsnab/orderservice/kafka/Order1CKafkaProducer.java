package ru.rfsnab.orderservice.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import ru.rfsnab.orderservice.models.dto.event.Order1CExportEvent;
import ru.rfsnab.orderservice.models.entity.DeliveryAddress;
import ru.rfsnab.orderservice.models.entity.Order;
import ru.rfsnab.orderservice.models.entity.enums.CustomerType;

import java.util.List;

/**
 * Отправка полного заказа в Kafka для выгрузки в 1С.
 * Топик "order-1c-export" — отдельный от "order-events" (уведомления).
 */
@Slf4j
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

        log.info("Sending order to 1C: orderId={}", order.getId());

        Order1CExportEvent event = Order1CExportEvent.builder()
                .orderId(order.getId())
                .orderNumber(order.getOrderNumber())
                .createdAt(order.getCreatedAt())
                .userId(order.getUserId())
                .customerEmail(order.getCustomerEmail())
                .customerType(order.getCustomerType() != null ? order.getCustomerType().name() : CustomerType.B2C.name())
                .companyName(order.getCompanyName())
                .inn(order.getInn())
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
                log.info("Order sent to 1C: orderId={}, topic={}, offset={}",
                        order.getId(), topics.getOrder1cExport(), result.getRecordMetadata().offset());
            } else {
                log.error("Failed to send order to 1C: orderId={}", order.getId(), ex);
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
