package ru.rfsnab.orderservice.models.dto.product;

import java.math.BigDecimal;

/**
 * DTO для получения данных о товаре из product-service, не для ответа на фронт.
 */
public record ProductDto(
        Long id,
        String name,
        BigDecimal price,
        Integer stockQuantity,
        Boolean active
) {
}
