package ru.rfsnab.notificationservice.models;

import java.time.LocalDateTime;

public record LegalEntityEvent(
        String eventType,
        Long legalEntityId,
        String inn,
        String companyName,
        String legalEntityEmail,
        String targetEmail,
        String rejectionReason,
        LocalDateTime timestamp,
        String token         // emailConfirmToken or linkToken; null when not applicable
) {}
