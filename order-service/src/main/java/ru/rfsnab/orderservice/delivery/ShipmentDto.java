package ru.rfsnab.orderservice.delivery;

import java.time.Instant;
import java.util.UUID;

/**
 * Отправление для админки и клиента.
 *
 * @param notFound    накладная не найдена в ТК (carrierStatusCode == NOT_FOUND)
 * @param trackingUrl ссылка «Отследить» на сайте ТК; null, если шаблон ссылки не задан
 */
public record ShipmentDto(
        Long id,
        UUID orderId,
        String orderNumber,
        CarrierCode carrierCode,
        String carrierName,
        String waybillNumber,
        ShipmentDirection direction,
        String sender,
        String receiver,
        String routeFrom,
        String routeTo,
        Instant shippedAt,
        Instant estimatedDeliveryAt,
        String carrierStatus,
        ShipmentStatusCode carrierStatusCode,
        Instant carrierStatusUpdatedAt,
        Instant polledAt,
        boolean pollingStopped,
        boolean notFound,
        ShipmentSource source,
        String trackingUrl,
        Instant createdAt
) {
    /** Копия для клиента: без контрагентов и направления накладной (они только для менеджера). */
    public ShipmentDto forClient() {
        return new ShipmentDto(id, orderId, orderNumber, carrierCode, carrierName, waybillNumber, null, null, null,
                routeFrom, routeTo, shippedAt, estimatedDeliveryAt, carrierStatus, carrierStatusCode,
                carrierStatusUpdatedAt, polledAt, pollingStopped, notFound, source, trackingUrl, createdAt);
    }

    /** Собирает DTO; carrier нужен для названия ТК и ссылки «Отследить» (null — название = код, ссылки нет). */
    public static ShipmentDto from(Shipment s, Carrier carrier) {
        return new ShipmentDto(s.getId(),
                s.getOrder() == null ? null : s.getOrder().getId(),
                s.getOrder() == null ? null : s.getOrder().getOrderNumber(),
                s.getCarrierCode(),
                carrier == null ? s.getCarrierCode().name() : carrier.getName(),
                s.getWaybillNumber(), s.getDirection(), s.getSender(), s.getReceiver(), s.getRouteFrom(), s.getRouteTo(),
                s.getShippedAt(), s.getEstimatedDeliveryAt(), s.getCarrierStatus(), s.getCarrierStatusCode(),
                s.getCarrierStatusUpdatedAt(), s.getPolledAt(), s.isPollingStopped(),
                s.getCarrierStatusCode() == ShipmentStatusCode.NOT_FOUND, s.getSource(),
                carrier == null ? null : carrier.trackingUrl(s.getWaybillNumber()), s.getCreatedAt());
    }
}
