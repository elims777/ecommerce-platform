package ru.rfsnab.orderservice.models.dto.order;

/**
 * Сводка по заказам для админки.
 *
 * @param total         все заказы
 * @param inProgress    заказы не в финальном статусе (см. OrderStatus.finalStatuses())
 * @param completed     доставленные или завершённые (DELIVERED, COMPLETED)
 * @param cancelled     отменённые или возвращённые (CANCELLED, REFUNDED)
 * @param newLast30Days заказы, созданные за последние 30 дней
 */
public record OrderStatsResponse(
        long total,
        long inProgress,
        long completed,
        long cancelled,
        long newLast30Days
) {
}
