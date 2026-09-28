package ru.rfsnab.orderservice.repository.projection;

/**
 * Сырые агрегаты по таблице orders — считаются одним проходом БД.
 */
public interface OrderStatsProjection {
    long getTotal();

    long getInProgress();

    long getCompleted();

    long getCancelled();

    long getNewLast30Days();
}
