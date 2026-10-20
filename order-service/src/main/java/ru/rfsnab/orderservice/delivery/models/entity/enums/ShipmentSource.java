package ru.rfsnab.orderservice.delivery.models.entity.enums;

/** Откуда появилось отправление: внесено менеджером или импортировано из журнала ТК. */
public enum ShipmentSource {
    MANUAL,
    IMPORT
}
