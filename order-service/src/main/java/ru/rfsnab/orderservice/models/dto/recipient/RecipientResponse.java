package ru.rfsnab.orderservice.models.dto.recipient;

import java.time.LocalDateTime;

public record RecipientResponse(
        Long id,
        String name,
        String phone,
        boolean isDefault,
        LocalDateTime createdAt
) {
}
