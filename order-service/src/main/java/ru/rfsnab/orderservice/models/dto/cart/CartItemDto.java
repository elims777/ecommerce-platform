package ru.rfsnab.orderservice.models.dto.cart;

import java.math.BigDecimal;

public record CartItemDto(
        Long productId,
        Long variantId,
        String variantAttributes,
        String productName,
        Integer quantity,
        BigDecimal price,
        BigDecimal subtotal
) {
}
