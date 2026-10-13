package ru.rfsnab.notificationservice.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Событие SHIPMENT_NOT_FOUND (топик order-events): накладная ТК не найдена в системе перевозчика.
 *
 * @param eventType     тип события
 * @param orderNumber   номер заказа
 * @param carrierName   название ТК
 * @param waybillNumber номер накладной
 * @param notFoundSince с какого момента накладная не находится (ISO-строка)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ShipmentNotFoundEvent(
        String eventType,
        String orderNumber,
        String carrierName,
        String waybillNumber,
        String notFoundSince
) {}
