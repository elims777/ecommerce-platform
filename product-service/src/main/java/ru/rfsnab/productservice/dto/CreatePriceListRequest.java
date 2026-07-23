package ru.rfsnab.productservice.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record CreatePriceListRequest(
        @NotEmpty(message = "Список категорий не может быть пустым")
        List<Long> categoryIds
) {
}
