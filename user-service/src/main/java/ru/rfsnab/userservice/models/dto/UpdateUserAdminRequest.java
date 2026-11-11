package ru.rfsnab.userservice.models.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateUserAdminRequest(
        @Size(max = 50, message = "Имя не должно превышать 50 символов") String firstname,
        @Size(max = 50, message = "Фамилия не должна превышать 50 символов") String lastname,
        @Pattern(regexp = "^(\\+7\\d{10})?$", message = "Телефон должен быть в формате +7XXXXXXXXXX")
        String phone) {}
