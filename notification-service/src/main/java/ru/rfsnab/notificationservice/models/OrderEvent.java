package ru.rfsnab.notificationservice.models;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Kafka event для заказов.
 * Своя копия — notification-service не зависит от order-service.
 */
public record OrderEvent(
        String eventType,
        UUID orderId,
        String orderNumber,
        Long userId,
        String status,
        BigDecimal totalAmount,
        String customerEmail,
        LocalDateTime timestamp
) {
}
