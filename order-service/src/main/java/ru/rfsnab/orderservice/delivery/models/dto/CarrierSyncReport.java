package ru.rfsnab.orderservice.delivery.models.dto;

import ru.rfsnab.orderservice.delivery.models.entity.enums.CarrierCode;
import ru.rfsnab.orderservice.delivery.models.entity.enums.SyncStatus;

import java.time.Instant;
import java.util.List;

/**
 * Отчёт о синхронизации терминалов одной ТК (спек 6.3): хранится в carriers.last_sync_report,
 * уходит в админку и в письмо менеджеру.
 *
 * @param skippedItems первые не более 50 пропущенных записей с причиной
 * @param moreSkipped  сколько пропущенных записей не вошло в skippedItems
 * @param message      текст ошибки или предупреждения
 */
public record CarrierSyncReport(
        CarrierCode carrierCode,
        String carrierName,
        SyncStatus status,
        int received,
        int added,
        int updated,
        int deactivated,
        int skipped,
        List<SkippedItem> skippedItems,
        int moreSkipped,
        String message,
        long durationMs,
        Instant finishedAt
) {

    /** Пропущенная запись и причина пропуска. */
    public record SkippedItem(String externalId, String reason) {
    }
}
