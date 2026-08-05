package ru.rfsnab.integrationservice.dto;

import lombok.Builder;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Kafka event по завершении ФТК-импорта (топик "import-events").
 * notification-service слушает и отправляет отчёт на почту.
 */
@Builder
public record FtkImportCompletedEvent(
        String eventType,
        String status,
        int totalReceived,
        int created,
        int updated,
        int unchanged,
        int failed,
        int imagesProcessed,
        int imagesFailed,
        long durationMs,
        LocalDateTime startedAt,
        List<ErrorItem> errors,
        /** Количество каскадных ошибок (следствие abort транзакции), не включённых в errors */
        int cascadeCount
) {
    public static final String EVENT_TYPE = "FTK_IMPORT_COMPLETED";

    public record ErrorItem(String externalId, String message, boolean cascade) {}
}
