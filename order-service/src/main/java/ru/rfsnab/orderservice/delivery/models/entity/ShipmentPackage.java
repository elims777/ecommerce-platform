package ru.rfsnab.orderservice.delivery.models.entity;

import java.math.BigDecimal;

/** Грузовое место отправления; любое поле может быть null, если ТК его не сообщает. */
public record ShipmentPackage(String number, BigDecimal weightKg, BigDecimal lengthCm, BigDecimal widthCm,
                              BigDecimal heightCm) {
}
