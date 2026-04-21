package ru.rfsnab.orderservice.models.dto.recipient;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RecipientAddressRequest(
        @NotBlank(message = "Метка обязательна")
        @Size(max = 50)
        String label,

        @NotBlank(message = "Город обязателен")
        @Size(max = 100)
        String city,

        @NotBlank(message = "Улица обязательна")
        @Size(max = 150)
        String street,

        @NotBlank(message = "Дом обязателен")
        @Size(max = 20)
        String building,

        @Size(max = 20)
        String apartment,

        @Size(max = 10)
        String postalCode,

        boolean isDefault
) {
}
