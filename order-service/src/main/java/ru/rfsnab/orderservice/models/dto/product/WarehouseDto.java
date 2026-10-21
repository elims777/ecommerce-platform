package ru.rfsnab.orderservice.models.dto.product;

/**
 * DTO склада из product-service (только активные), не для ответа на фронт.
 */
public record WarehouseDto(
        Long id,
        String code,
        String name,
        String city,
        boolean pickupAvailable
) {
}
