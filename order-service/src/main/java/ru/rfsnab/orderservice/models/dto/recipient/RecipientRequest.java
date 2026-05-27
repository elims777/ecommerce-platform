package ru.rfsnab.orderservice.models.dto.recipient;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RecipientRequest(
        @NotBlank(message = "Имя обязательно")
        @Size(max = 200, message = "Максимум 200 символов")
        String name,

        @NotBlank(message = "Телефон обязателен")
        @Pattern(regexp = "^\\+?[0-9]{11}$", message = "Телефон должен содержать 11 цифр")
        String phone,

        boolean isDefault
) {
}
