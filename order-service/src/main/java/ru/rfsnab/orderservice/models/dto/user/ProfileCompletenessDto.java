package ru.rfsnab.orderservice.models.dto.user;

import java.util.List;

/**
 * DTO для получения статуса заполненности профиля из user-service.
 */
public record ProfileCompletenessDto(
        boolean complete,
        List<String> missing
) {
}