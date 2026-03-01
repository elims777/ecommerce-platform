package ru.rfsnab.userservice.models.dto;

import lombok.Builder;

/**
 * DTO для отображения сохранённого адреса пользователя.
 */
@Builder
public record UserAddressDto(
        Long id,
        String label,
        String recipientName,
        String phone,
        String city,
        String street,
        String building,
        String apartment,
        String entrance,
        String floor,
        String intercomCode,
        String postalCode,
        String deliveryInstructions,
        boolean isDefault
) {
}
