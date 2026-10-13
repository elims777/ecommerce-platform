package ru.rfsnab.orderservice.delivery;

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
    /** Копия для клиента: без служебных полей синхронизации и состояния ключа API. */
    public CarrierDto forClient() {
        return new CarrierDto(code, name, enabled, addressDeliveryEnabled, terminalDeliveryEnabled, sortOrder,
                false, null, 0, null, trackingUrlTemplate, statusTrackingSupported);
    }

    /** Собирает DTO из сущности. */
    public static CarrierDto from(Carrier carrier, boolean apiKeyConfigured, CarrierSyncReport lastSyncReport,
                                  boolean statusTrackingSupported) {
        return new CarrierDto(carrier.getCode(), carrier.getName(), carrier.isEnabled(),
                carrier.isAddressDeliveryEnabled(), carrier.isTerminalDeliveryEnabled(), carrier.getSortOrder(),
                apiKeyConfigured, carrier.getTerminalsSyncedAt(), carrier.getActiveTerminalsCount(), lastSyncReport,
                carrier.getTrackingUrlTemplate(), statusTrackingSupported);
    }
}
