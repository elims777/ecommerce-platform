package ru.rfsnab.userservice.models.dto.legal;

import jakarta.validation.constraints.NotBlank;

/**
 * Запрос на добавление адреса доставки юридического лица.
 */
public record SaveLegalEntityAddressRequest(
        @NotBlank String city,
        @NotBlank String street,
        @NotBlank String building,
        String apartment,
        String postalCode,
        boolean primary
) {}
