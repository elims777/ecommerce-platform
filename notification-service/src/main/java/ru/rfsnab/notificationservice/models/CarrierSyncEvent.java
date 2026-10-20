package ru.rfsnab.notificationservice.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Событие CARRIER_SYNC_COMPLETED (топик order-events): итог синхронизации терминалов ТК.
 *
 * @param eventType тип события
 * @param trigger   SCHEDULED | STARTUP | MANUAL
 * @param reports   отчёты по каждой синхронизированной ТК
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CarrierSyncEvent(
        String eventType,
        String trigger,
        List<CarrierSyncReport> reports
) {

    /**
     * Отчёт синхронизации одной ТК.
     *
     * @param status       OK | WARNING | ERROR | NOT_CONFIGURED
     * @param skippedItems первые пропущенные записи с причинами
     * @param moreSkipped  сколько пропущенных не вошло в skippedItems
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CarrierSyncReport(
            String carrierCode,
            String carrierName,
            String status,
            int received,
            int added,
            int updated,
            int deactivated,
            int skipped,
            List<SkippedItem> skippedItems,
            int moreSkipped,
            String message,
            long durationMs,
            String finishedAt
    ) {}

    /**
     * Пропущенная запись и причина пропуска.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SkippedItem(String externalId, String reason) {}
}
