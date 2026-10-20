package ru.rfsnab.orderservice.delivery.models.entity;

import ru.rfsnab.orderservice.delivery.models.entity.enums.ShipmentPayerRole;

/**
 * Плательщик услуг отправления; разные услуги могут оплачивать разные стороны.
 *
 * @param role известна только у ПЭК; null — фронт определяет сторону по ИНН/названию
 */
public record ShipmentPayer(String name, String inn, ShipmentPayerRole role) {
}
