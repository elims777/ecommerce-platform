package ru.rfsnab.orderservice.models.dto.order;

import lombok.Builder;

/**
 * DTO для отображения точки склада / самовывоза покупателю.
 */
@Builder
public record WarehousePointDto(
        Long id,
        String name,
        String city,
        String street,
        String building,
        String postalCode,
        String phone,
        String workingHours,
        String description,
        boolean active
) {}
