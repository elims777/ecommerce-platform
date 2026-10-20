package ru.rfsnab.orderservice.delivery.models.entity.enums;

/** Направление отправления относительно компании (по ИНН отправителя и получателя). */
public enum ShipmentDirection {
    OUTGOING,
    INCOMING,
    UNKNOWN
}
