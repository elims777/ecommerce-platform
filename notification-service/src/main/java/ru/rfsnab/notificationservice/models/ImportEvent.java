package ru.rfsnab.notificationservice.models;

import java.time.LocalDateTime;
import java.util.List;

public record ImportEvent(
        String eventType,
        String status,               // SUCCESS | PARTIAL | FAILED
        int totalReceived,
        int created,
        int updated,
        int unchanged,
        int failed,
        int imagesProcessed,
        int imagesFailed,
        long durationMs,
        LocalDateTime startedAt,
        int cascadeCount,
        List<ImportError> errors
) {
    public record ImportError(String externalId, String message, boolean cascade) {}
}
