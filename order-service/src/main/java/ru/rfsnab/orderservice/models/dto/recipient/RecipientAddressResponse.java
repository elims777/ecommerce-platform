package ru.rfsnab.orderservice.models.dto.recipient;

import java.time.LocalDateTime;

public record RecipientAddressResponse(
        Long id,
        Long recipientId,
        String label,
        String city,
        String street,
        String building,
        String apartment,
        String postalCode,
        boolean isDefault,
        LocalDateTime createdAt
) {
}
