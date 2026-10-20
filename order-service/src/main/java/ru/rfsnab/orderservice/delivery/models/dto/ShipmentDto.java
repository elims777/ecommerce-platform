package ru.rfsnab.orderservice.delivery.models.dto;

import ru.rfsnab.orderservice.delivery.models.entity.enums.CarrierCode;
import ru.rfsnab.orderservice.delivery.models.entity.enums.ShipmentDirection;
import ru.rfsnab.orderservice.delivery.models.entity.enums.ShipmentSource;
import ru.rfsnab.orderservice.delivery.models.entity.enums.ShipmentStatusCode;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
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
        String senderInn,
        String receiver,
        String receiverInn,
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
        Instant createdAt,
        BigDecimal weightKg,
        BigDecimal volumeM3,
        Integer placesCount,
        BigDecimal cost,
        Boolean paid,
        Boolean fromAddress,
        Boolean toAddress,
        Instant deliveredAt,
        List<ShipmentDocumentDto> documents,
        List<ShipmentPackageDto> packages,
        List<ShipmentPayerDto> payers
) {
}
