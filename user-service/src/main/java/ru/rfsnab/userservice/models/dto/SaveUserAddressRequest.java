package ru.rfsnab.userservice.models.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Builder;

/**
 * Запрос на создание / обновление адреса.
 * Используется и для POST (создание), и для PUT (обновление).
 */
@Builder
public record SaveUserAddressRequest(
        @NotBlank(message = "Название адреса обязательно")
        @Size(max = 50, message = "Название не должно превышать 50 символов")
        String label,

        @NotBlank(message = "Имя получателя обязательно")
        @Size(max = 100)
        String recipientName,

        @NotBlank(message = "Телефон обязателен")
        @Size(max = 20)
        String phone,

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

        @Size(max = 20)
        String entrance,

        @Size(max = 10)
        String floor,

        @Size(max = 50)
        String intercomCode,

        @Size(max = 10)
        String postalCode,

        @Size(max = 500)
        String deliveryInstructions,

        boolean defaultAddress
) {}
