package ru.rfsnab.orderservice.delivery;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record CarrierUpdateRequest(
        @NotNull Boolean enabled,
        @NotNull Boolean addressDeliveryEnabled,
        @NotNull @Min(0) Integer sortOrder
) {
}
