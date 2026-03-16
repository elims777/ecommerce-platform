package ru.rfsnab.orderservice.models.dto.order;

import ru.rfsnab.orderservice.models.entity.enums.OrderStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * OrderSummaryDto для списков с пагинацией.
 */
public record OrderSummaryDto(
        UUID id,
        String orderNumber,
        String externalId,
        OrderStatus status,
        int itemsCount,
        BigDecimal totalAmount,
        String customerEmail,
        LocalDateTime createdAt
) {
}
