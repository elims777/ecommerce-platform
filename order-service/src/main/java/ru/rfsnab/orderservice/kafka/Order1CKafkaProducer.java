package ru.rfsnab.orderservice.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import ru.rfsnab.orderservice.models.dto.event.Order1CExportEvent;
import ru.rfsnab.orderservice.models.entity.DeliveryAddress;
import ru.rfsnab.orderservice.models.entity.Order;
import ru.rfsnab.orderservice.models.entity.enums.CustomerType;
import ru.rfsnab.orderservice.repository.WarehousePointRepository;

import java.util.ArrayList;
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
    private final WarehousePointRepository warehousePointRepository;

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
                .customerName(order.getCustomerName())
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
                .comment(buildComment(order, address, isPickup))
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

    /**
     * Собирает комментарий заказа для 1С: способ доставки + адрес (доставки или склада)
     * + получатель. Дублирует данные, которые 1С не показывает в карточке контрагента,
     * чтобы менеджер видел всё в одном месте.
     */
    private String buildComment(Order order, DeliveryAddress address, boolean isPickup) {
        List<String> lines = new ArrayList<>();

        if (order.getComment() != null && !order.getComment().isBlank()) {
            lines.add(order.getComment());
        }

        lines.add("Способ доставки: " + order.getDeliveryMethod().getDisplayName());

        if (isPickup) {
            if (order.getWarehousePointId() != null) {
                warehousePointRepository.findById(order.getWarehousePointId()).ifPresent(wp ->
                        lines.add("Пункт самовывоза: " + wp.getName() + ", "
                                + joinAddress(wp.getPostalCode(), wp.getCity(), wp.getStreet(), wp.getBuilding(), null)));
            }
            if (order.getPickupRecipientName() != null) {
                lines.add("Получатель: " + order.getPickupRecipientName()
                        + (order.getPickupRecipientPhone() != null ? ", " + order.getPickupRecipientPhone() : ""));
            }
        } else if (address != null) {
            lines.add("Адрес доставки: " + joinAddress(address.getPostalCode(), address.getCity(),
                    address.getStreet(), address.getBuilding(), address.getApartment()));
            if (address.getRecipientName() != null) {
                lines.add("Получатель: " + address.getRecipientName()
                        + (address.getPhone() != null ? ", " + address.getPhone() : ""));
            }
        }

        return String.join("\n", lines);
    }

    /** Склеивает непустые части адреса через запятую; квартиру — с префиксом «кв.». */
    private String joinAddress(String postalCode, String city, String street, String building, String apartment) {
        List<String> parts = new ArrayList<>();
        if (postalCode != null) parts.add(postalCode);
        if (city != null) parts.add(city);
        if (street != null) parts.add(street);
        if (building != null) parts.add("д. " + building);
        if (apartment != null) parts.add("кв. " + apartment);
        return String.join(", ", parts);
    }

    private List<Order1CExportEvent.ExportOrderItem> mapItems(Order order){
        return order.getItems().stream()
                .map(item -> Order1CExportEvent.ExportOrderItem.builder()
                        .productId(item.getProductId())
                        .externalId(item.getExternalId())
                        .productName(item.getProductName())
                        .sku(item.getSku())
                        .unitOfMeasure(item.getUnitOfMeasure())
                        .quantity(item.getQuantity())
                        .price(item.getPrice())
                        .build())
                .toList();
    }
}
