package ru.rfsnab.userservice.models.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Самостоятельное редактирование профиля. Email, пароль и роли здесь изменить нельзя.
 */
public record UpdateUserRequest(
        @NotBlank @Size(max = 50, message = "Имя не должно превышать 50 символов") String firstname,
        @NotBlank @Size(max = 50, message = "Фамилия не должна превышать 50 символов") String lastname,
        @Size(max = 50, message = "Отчество не должно превышать 50 символов") String surname,
        @NotBlank(message = "Телефон обязателен")
        @Pattern(regexp = "^\\+?\\d{11}$", message = "Телефон должен содержать 11 цифр")
        String phone) {}
