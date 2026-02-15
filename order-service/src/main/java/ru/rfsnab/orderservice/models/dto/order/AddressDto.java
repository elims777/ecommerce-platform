package ru.rfsnab.orderservice.models.dto.order;

import jakarta.validation.constraints.NotBlank;
import lombok.Builder;

@Builder
public record AddressDto(
        @NotBlank(message = "City is required")
        String city,

        @NotBlank(message = "Street is required")
        String street,

        @NotBlank(message = "Building is required")
        String building,

        String apartment,

        String postalCode,

        @NotBlank(message = "Phone is required")
        String phone,

        @NotBlank(message = "Recipient name is required")
        String recipientName
) {
}
