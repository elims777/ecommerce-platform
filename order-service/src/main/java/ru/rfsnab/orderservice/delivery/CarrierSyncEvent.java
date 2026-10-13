package ru.rfsnab.orderservice.delivery;

import java.util.List;

/**
 * Kafka-событие CARRIER_SYNC_COMPLETED (топик order-events): отчёты синхронизации терминалов для письма менеджеру.
 */
public record CarrierSyncEvent(String eventType, SyncTrigger trigger, List<CarrierSyncReport> reports) {

    /** Тип события в поле eventType. */
    public static final String TYPE = "CARRIER_SYNC_COMPLETED";

    /** Создаёт событие с типом {@link #TYPE}. */
    public static CarrierSyncEvent of(SyncTrigger trigger, List<CarrierSyncReport> reports) {
        return new CarrierSyncEvent(TYPE, trigger, reports);
    }
}
