package ru.rfsnab.orderservice.models.dto.order;

import java.math.BigDecimal;

public record OrderItemDto(
        Long productId,
        String productName,
        Integer quantity,
        BigDecimal price,
        BigDecimal subtotal
) {
}
