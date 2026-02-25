package ru.rfsnab.orderservice.models.dto.event;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Kafka event для заказов.
 */
@Builder
public record OrderEvent(
        String eventType,
        UUID orderId,
        String orderNumber,
        Long userId,
        String status,
        BigDecimal totalAmount,
        String customerEmail,
        LocalDateTime timestamp
) {}
