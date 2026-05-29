package ru.rfsnab.integrationservice.dto;

import ru.rfsnab.integrationservice.model.ImportLog;

import java.time.LocalDateTime;

public record ImportLogDto(
        Long id,
        String exchangeType,
        String status,
        int totalReceived,
        int created,
        int updated,
        int failed,
        Long durationMs,
        String errorMessage,
        LocalDateTime createdAt
) {
    public static ImportLogDto from(ImportLog log) {
        return new ImportLogDto(
                log.getId(),
                log.getExchangeType(),
                log.getStatus().name(),
                log.getTotalReceived(),
                log.getCreated(),
                log.getUpdated(),
                log.getFailed(),
                log.getDurationMs(),
                log.getErrorMessage(),
                log.getCreatedAt()
        );
    }
}
