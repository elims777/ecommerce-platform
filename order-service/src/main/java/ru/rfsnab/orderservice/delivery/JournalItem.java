package ru.rfsnab.orderservice.delivery;

import java.time.Instant;

/**
 * Отправление из журнала ТК.
 *
 * @param siteOrderNumber номер заказа клиента, указанный при оформлении в ТК; null, если не указан
 * @param status          текущий статус; null, если журнал его не содержит
 */
public record JournalItem(
        String waybillNumber,
        String sender,
        String senderInn,
        String receiver,
        String receiverInn,
        String routeFrom,
        String routeTo,
        Instant shippedAt,
        Instant estimatedDeliveryAt,
        String siteOrderNumber,
        CarrierShipmentStatus status
) {
}
