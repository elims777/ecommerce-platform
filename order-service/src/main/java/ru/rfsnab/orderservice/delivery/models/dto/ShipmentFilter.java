package ru.rfsnab.orderservice.delivery.models.dto;

import ru.rfsnab.orderservice.delivery.models.entity.enums.CarrierCode;
import ru.rfsnab.orderservice.delivery.models.entity.enums.ShipmentDirection;
import ru.rfsnab.orderservice.delivery.models.entity.enums.ShipmentStatusCode;

import java.time.Instant;

/**
 * Фильтры журнала отправлений. Пустые (null/false) условия не применяются.
 *
 * @param city         подстрока города отправления или назначения без учёта регистра
 * @param counterparty подстрока в отправителе или получателе без учёта регистра
 * @param search       подстрока в номере накладной или номере заказа сайта без учёта регистра
 * @param from         начало периода (включительно); период считается по дате отгрузки, а без неё по дате внесения
 * @param to           конец периода (исключительно)
 * @param problem      только со статусом PROBLEM
 * @param notFound     только «накладная не найдена»
 * @param stale        только «нет движения» (опрос остановлен)
 * @param awaitingMore только заказы в режиме MULTIPLE, где менеджер ещё не нажал «Все отправления внесены»
 */
public record ShipmentFilter(
        CarrierCode carrier,
        ShipmentDirection direction,
        ShipmentStatusCode statusCode,
        String city,
        String counterparty,
        String search,
        Instant from,
        Instant to,
        boolean problem,
        boolean notFound,
        boolean stale,
        boolean awaitingMore
) {
}
