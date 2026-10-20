package ru.rfsnab.orderservice.delivery.models.dto.event;

import ru.rfsnab.orderservice.delivery.models.dto.CarrierSyncReport;
import ru.rfsnab.orderservice.delivery.models.entity.enums.SyncTrigger;

import java.util.List;

/**
 * Kafka-событие CARRIER_SYNC_COMPLETED (топик order-events): отчёты синхронизации терминалов для письма менеджеру.
 */
public record CarrierSyncEvent(String eventType, SyncTrigger trigger, List<CarrierSyncReport> reports) {

    /** Тип события в поле eventType. */
    public static final String TYPE = "CARRIER_SYNC_COMPLETED";
}
