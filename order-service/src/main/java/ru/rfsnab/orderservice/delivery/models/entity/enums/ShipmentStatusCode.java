package ru.rfsnab.orderservice.delivery.models.entity.enums;

/** Нормализованный статус отправления (спек 6.4). */
public enum ShipmentStatusCode {
    /** Заказ/заявка оформлены в ТК, груз ещё не сдан. */
    CREATED,
    /** Принят ТК или в пути. */
    IN_TRANSIT,
    /** Прибыл на терминал получателя или готов к выдаче. */
    ARRIVED_AT_TERMINAL,
    /** Выдан или доставлен. */
    DELIVERED,
    /** Отменён или возвращён у ТК (финальный). */
    CANCELLED,
    /** Проблема или задержка: только подсветка. */
    PROBLEM,
    /** ТК не знает такую накладную. */
    NOT_FOUND
}
