package ru.rfsnab.orderservice.delivery;

import java.time.Instant;
import java.util.List;

/**
 * Отчёт о синхронизации терминалов одной ТК (спек 6.3): хранится в carriers.last_sync_report,
 * уходит в админку и в письмо менеджеру.
 *
 * @param skippedItems первые {@link CarrierSyncService#MAX_SKIPPED_ITEMS} пропущенных записей с причиной
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

    /** Отчёт без счётчиков — для ERROR, NOT_CONFIGURED и «справочник не изменился». */
    public static CarrierSyncReport withoutCounters(Carrier carrier, SyncStatus status, String message, long durationMs) {
        return new CarrierSyncReport(carrier.getCode(), carrier.getName(), status, 0, 0, 0, 0, 0,
                List.of(), 0, message, durationMs, Instant.now());
    }
}
