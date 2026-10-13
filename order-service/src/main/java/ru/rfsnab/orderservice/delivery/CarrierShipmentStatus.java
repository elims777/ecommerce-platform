package ru.rfsnab.orderservice.delivery;

/**
 * Статус накладной, как его вернула ТК.
 *
 * @param code      нормализованный код; null, если сырой статус неизвестен таблице сопоставления
 * @param rawText   сырой текст (или код) статуса ТК
 * @param routeFrom город отправления, если ТК отдаёт
 * @param routeTo   город назначения, если ТК отдаёт
 */
public record CarrierShipmentStatus(ShipmentStatusCode code, String rawText, String routeFrom, String routeTo) {
}
