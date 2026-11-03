package ru.rfsnab.orderservice.delivery.repository.projection;

/**
 * Счётчики активных отправлений по направлениям — считаются одним проходом БД.
 */
public interface ShipmentCountsProjection {
    long getIncoming();

    long getOutgoing();
}
