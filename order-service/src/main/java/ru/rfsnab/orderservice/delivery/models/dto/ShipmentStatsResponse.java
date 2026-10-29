package ru.rfsnab.orderservice.delivery.models.dto;

/**
 * Сводка для карточки «Логистика» на дашборде админки.
 *
 * @param incoming        активные входящие отправления
 * @param outgoing        активные исходящие отправления
 * @param awaitingPayment неоплаченные отправления, которые оплачиваем мы
 */
public record ShipmentStatsResponse(long incoming, long outgoing, long awaitingPayment) {
}
