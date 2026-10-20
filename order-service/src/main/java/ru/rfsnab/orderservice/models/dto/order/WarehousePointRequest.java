package ru.rfsnab.orderservice.models.dto.order;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;

/**
 * Создание / редактирование точки самовывоза из админки.
 */
public record WarehousePointRequest(
        @NotBlank @Size(max = 100) String name,
        @NotBlank @Size(max = 100) String city,
        @NotBlank @Size(max = 150) String street,
        @NotBlank @Size(max = 20) String building,
        @Size(max = 10) String postalCode, // в БД nullable
        @NotBlank @Size(max = 20) String phone,
        @Size(max = 200) String workingHours,
        @Size(max = 500) String description,
        @DecimalMin("-90") @DecimalMax("90") BigDecimal latitude,
        @DecimalMin("-180") @DecimalMax("180") BigDecimal longitude,
        boolean active
) {
}
