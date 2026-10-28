package ru.rfsnab.userservice.models.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateUserAdminRequest(
        @Size(max = 50, message = "Имя не должно превышать 50 символов") String firstname,
        @Size(max = 50, message = "Фамилия не должна превышать 50 символов") String lastname,
        @Pattern(regexp = "^(\\+?\\d{11})?$", message = "Телефон должен содержать 11 цифр")
        String phone) {}
