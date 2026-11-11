package ru.rfsnab.orderservice.models.dto.order;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
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
        @Pattern(regexp = "^\\+7\\d{10}$", message = "Телефон должен быть в формате +7XXXXXXXXXX")
        String phone,

        @NotBlank(message = "Recipient name is required")
        String recipientName
) {
}
