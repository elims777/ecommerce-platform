package ru.rfsnab.orderservice.models.dto.cart;

import java.math.BigDecimal;
import java.util.List;

public record CartDto(
        Long userId,
        List<CartItemDto> items,
        int totalItems,
        BigDecimal totalAmount
) {
}
