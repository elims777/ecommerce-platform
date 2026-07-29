package ru.rfsnab.orderservice.models.dto.order;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Документ заказа для отдачи клиенту/админу.
 * fileKey (внутренний путь в S3) намеренно не включён.
 */
public record OrderDocumentDto(
        UUID id,
        String type,
        String typeName,
        String fileName,
        String contentType,
        Long sizeBytes,
        LocalDateTime uploadedAt
) {
}
