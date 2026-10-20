package ru.rfsnab.notificationservice.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Kafka event для заказов.
 * Своя копия — notification-service не зависит от order-service.
 * Поля и вложенные record'ы должны совпадать с order-service OrderEvent.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OrderEvent(
        String eventType,
        UUID orderId,
        String orderNumber,
        Long userId,
        String status,
        BigDecimal totalAmount,
        String customerEmail,
        String customerName,
        String customerPhone,
        LocalDateTime timestamp,
        List<OrderItemLine> items,
        String deliveryMethod,
        String paymentMethod,
        String comment,
        DeliveryAddressDto deliveryAddress,
        String customerType,
        String companyName,
        String inn,
        PickupPointDto pickupPoint,
        String documentType,
        String documentFileName,
        String carrierName,
        String terminalName,
        String terminalAddress,
        List<ShipmentLine> shipments
) {
    public record OrderItemLine(String productName, Integer quantity, BigDecimal price, String variantAttributes, String sku) {}

    /** Отправление (накладная ТК) для блока доставки в письме о смене статуса; поля nullable. */
    public record ShipmentLine(String carrierName, String waybillNumber, String trackingUrl, String carrierStatus) {}

    public record DeliveryAddressDto(String city, String street, String building, String apartment,
                                     String postalCode, String phone, String recipientName) {}

    public record PickupPointDto(String name, String city, String street, String building,
                                  String postalCode, String phoneNumber, String workingHours) {}
}
