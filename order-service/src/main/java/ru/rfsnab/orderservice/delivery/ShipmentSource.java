package ru.rfsnab.orderservice.delivery;

/** Откуда появилось отправление: внесено менеджером или импортировано из журнала ТК. */
public enum ShipmentSource {
    MANUAL,
    IMPORT
}
