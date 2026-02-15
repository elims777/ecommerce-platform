package ru.rfsnab.orderservice.models.dto.order;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record OrderItemDto(
        @NotNull(message = "ID товара обязателен")
        Long productId,
        String productName,
        @NotNull(message = "Количество обязательно")
        @Min(value = 1, message = "Количество должно быть не менее 1")
        Integer quantity,
        BigDecimal price,
        BigDecimal subtotal
) {
}
