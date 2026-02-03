package ru.rfsnab.orderservice.models.dto.order;

import ru.rfsnab.orderservice.models.entity.enums.OrderStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record OrderSummaryDto(
        UUID id,
        String orderNumber,
        OrderStatus status,
        String statusDisplayName,
        int itemsCount,
        BigDecimal totalAmount,
        LocalDateTime createdAt
) {
}
