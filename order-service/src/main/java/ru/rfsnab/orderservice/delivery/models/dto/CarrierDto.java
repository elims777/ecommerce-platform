package ru.rfsnab.orderservice.delivery.models.dto;

import ru.rfsnab.orderservice.delivery.models.entity.enums.CarrierCode;

import java.time.Instant;

/**
 * Транспортная компания для клиента и админки. Служебные поля синхронизации нужны только админке.
 *
 * @param apiKeyConfigured       задан ли ключ API ТК в env
 * @param lastSyncReport         итог последней синхронизации терминалов; null, если её не было
 * @param trackingUrlTemplate    ссылка «Отследить» с {number}; null, если не задана
 * @param statusTrackingSupported умеем ли мы опрашивать статусы накладных этой ТК
 */
public record CarrierDto(
        CarrierCode code,
        String name,
        boolean enabled,
        boolean addressDeliveryEnabled,
        boolean terminalDeliveryEnabled,
        int sortOrder,
        boolean apiKeyConfigured,
        Instant terminalsSyncedAt,
        int activeTerminalsCount,
        CarrierSyncReport lastSyncReport,
        String trackingUrlTemplate,
        boolean statusTrackingSupported
) {
}
