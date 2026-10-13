package ru.rfsnab.orderservice.delivery;

/** Направление отправления относительно компании (по ИНН отправителя и получателя). */
public enum ShipmentDirection {
    OUTGOING,
    INCOMING,
    UNKNOWN
}
