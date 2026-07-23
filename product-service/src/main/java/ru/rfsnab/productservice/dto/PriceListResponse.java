package ru.rfsnab.productservice.dto;

import java.time.LocalDateTime;
import java.util.List;

public record PriceListResponse(
        Long id,
        String status,
        List<String> categoryNames,
        Integer rowCount,
        LocalDateTime createdAt,
        LocalDateTime completedAt
) {
}
