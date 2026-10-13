package ru.rfsnab.orderservice.delivery;

import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import ru.rfsnab.orderservice.models.entity.Order;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Фильтры журнала отправлений. Пустые (null/false) условия не применяются.
 *
 * @param city         подстрока города отправления или назначения без учёта регистра
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
        Instant from,
        Instant to,
        boolean problem,
        boolean notFound,
        boolean stale,
        boolean awaitingMore
) {
    private static final char LIKE_ESCAPE = '\\';

    /**
     * Условие выборки. Заказ подтягивается тем же запросом (fetch join) — имя заказа в DTO без N+1;
     * в count-запросе вместо fetch используется обычный join.
     */
    @SuppressWarnings("unchecked")
    public Specification<Shipment> toSpecification() {
        return (root, query, cb) -> {
            boolean countQuery = Long.class.equals(query.getResultType()) || long.class.equals(query.getResultType());
            Join<Shipment, Order> order = countQuery
                    ? root.join("order", JoinType.LEFT)
                    : (Join<Shipment, Order>) root.<Shipment, Order>fetch("order", JoinType.LEFT);
            List<Predicate> predicates = new ArrayList<>();
            if (carrier != null) {
                predicates.add(cb.equal(root.get("carrierCode"), carrier));
            }
            if (direction != null) {
                predicates.add(cb.equal(root.get("direction"), direction));
            }
            if (statusCode != null) {
                predicates.add(cb.equal(root.get("carrierStatusCode"), statusCode));
            }
            if (city != null && !city.isBlank()) {
                String pattern = "%" + escapeLike(city.trim().toLowerCase(Locale.ROOT)) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("routeFrom")), pattern, LIKE_ESCAPE),
                        cb.like(cb.lower(root.get("routeTo")), pattern, LIKE_ESCAPE)));
            }
            var date = cb.<Instant>coalesce(root.get("shippedAt"), root.get("createdAt"));
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(date, from));
            }
            if (to != null) {
                predicates.add(cb.lessThan(date, to));
            }
            if (problem) {
                predicates.add(cb.equal(root.get("carrierStatusCode"), ShipmentStatusCode.PROBLEM));
            }
            if (notFound) {
                predicates.add(cb.equal(root.get("carrierStatusCode"), ShipmentStatusCode.NOT_FOUND));
            }
            if (stale) {
                predicates.add(cb.isTrue(root.get("pollingStopped")));
            }
            if (awaitingMore) {
                predicates.add(cb.equal(order.get("shipmentMode"), ShipmentMode.MULTIPLE));
                predicates.add(cb.isFalse(order.get("shipmentsComplete")));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
