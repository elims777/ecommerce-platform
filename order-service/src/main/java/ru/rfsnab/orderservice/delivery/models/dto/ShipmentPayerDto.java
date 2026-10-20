package ru.rfsnab.orderservice.delivery.models.dto;

import ru.rfsnab.orderservice.delivery.models.entity.enums.ShipmentPayerRole;

/**
 * Плательщик услуг отправления.
 *
 * @param role известна только у ПЭК; null — фронт определяет сторону по ИНН/названию
 */
public record ShipmentPayerDto(String name, String inn, ShipmentPayerRole role) {
}
