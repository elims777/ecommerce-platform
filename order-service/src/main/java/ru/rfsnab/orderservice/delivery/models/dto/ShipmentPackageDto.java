package ru.rfsnab.orderservice.delivery.models.dto;

import java.math.BigDecimal;

/** Грузовое место отправления; любое поле может быть null, если ТК его не сообщает. */
public record ShipmentPackageDto(String number, BigDecimal weightKg, BigDecimal lengthCm, BigDecimal widthCm,
                                 BigDecimal heightCm) {
}
