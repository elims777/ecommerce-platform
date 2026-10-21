package ru.rfsnab.productservice.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record WarehouseRequest(
        @NotBlank @Size(max = 30) @Pattern(regexp = "[A-Z0-9_]+", message = "Код: только A-Z, 0-9 и _") String code,
        @NotBlank @Size(max = 100) String name,
        @NotBlank @Size(max = 100) String city,
        boolean pickupAvailable,
        boolean active,
        @Min(0) int sortOrder) {
}
