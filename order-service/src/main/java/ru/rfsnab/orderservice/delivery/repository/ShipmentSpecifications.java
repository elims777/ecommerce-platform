package ru.rfsnab.orderservice.delivery.repository;

import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import ru.rfsnab.orderservice.delivery.models.dto.ShipmentFilter;
import ru.rfsnab.orderservice.delivery.models.entity.Shipment;
import ru.rfsnab.orderservice.delivery.models.entity.enums.ShipmentMode;
import ru.rfsnab.orderservice.delivery.models.entity.enums.ShipmentStatusCode;
import ru.rfsnab.orderservice.models.entity.Order;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Спецификации журнала отправлений. */
public final class ShipmentSpecifications {

    private static final char LIKE_ESCAPE = '\\';

    /**
     * Условие выборки. Заказ подтягивается тем же запросом (fetch join) — имя заказа в DTO без N+1;
     * в count-запросе вместо fetch используется обычный join.
     */
    @SuppressWarnings("unchecked")
    public static Specification<Shipment> of(ShipmentFilter filter) {
        return (root, query, cb) -> {
            boolean countQuery = Long.class.equals(query.getResultType()) || long.class.equals(query.getResultType());
            Join<Shipment, Order> order = countQuery
                    ? root.join("order", JoinType.LEFT)
                    : (Join<Shipment, Order>) root.<Shipment, Order>fetch("order", JoinType.LEFT);
            List<Predicate> predicates = new ArrayList<>();
            if (filter.carrier() != null) {
                predicates.add(cb.equal(root.get("carrierCode"), filter.carrier()));
            }
            if (filter.direction() != null) {
                predicates.add(cb.equal(root.get("direction"), filter.direction()));
            }
            if (filter.statusCode() != null) {
                predicates.add(cb.equal(root.get("carrierStatusCode"), filter.statusCode()));
            }
            if (filter.city() != null && !filter.city().isBlank()) {
                String pattern = "%" + escapeLike(filter.city().trim().toLowerCase(Locale.ROOT)) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("routeFrom")), pattern, LIKE_ESCAPE),
                        cb.like(cb.lower(root.get("routeTo")), pattern, LIKE_ESCAPE)));
            }
            if (filter.counterparty() != null && !filter.counterparty().isBlank()) {
                String pattern = "%" + escapeLike(filter.counterparty().trim().toLowerCase(Locale.ROOT)) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("sender")), pattern, LIKE_ESCAPE),
                        cb.like(cb.lower(root.get("receiver")), pattern, LIKE_ESCAPE)));
            }
            if (filter.search() != null && !filter.search().isBlank()) {
                String pattern = "%" + escapeLike(filter.search().trim().toLowerCase(Locale.ROOT)) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("waybillNumber")), pattern, LIKE_ESCAPE),
                        cb.like(cb.lower(order.get("orderNumber")), pattern, LIKE_ESCAPE)));
            }
            var date = cb.<Instant>coalesce(root.get("shippedAt"), root.get("createdAt"));
            if (!countQuery) {
                // явный sort из Pageable Spring Data ставит поверх; без него — сначала активные, затем завершённые
                // (DELIVERED/CANCELLED), внутри групп свежие отгрузки сверху
                var finished = cb.<Integer>selectCase()
                        .when(root.get("carrierStatusCode").in(ShipmentStatusCode.DELIVERED, ShipmentStatusCode.CANCELLED), 1)
                        .otherwise(0);
                query.orderBy(cb.asc(finished), cb.desc(date), cb.desc(root.get("id")));
            }
            if (filter.from() != null) {
                predicates.add(cb.greaterThanOrEqualTo(date, filter.from()));
            }
            if (filter.to() != null) {
                predicates.add(cb.lessThan(date, filter.to()));
            }
            if (filter.problem()) {
                predicates.add(cb.equal(root.get("carrierStatusCode"), ShipmentStatusCode.PROBLEM));
            }
            if (filter.notFound()) {
                predicates.add(cb.equal(root.get("carrierStatusCode"), ShipmentStatusCode.NOT_FOUND));
            }
            if (filter.stale()) {
                predicates.add(cb.isTrue(root.get("pollingStopped")));
            }
            if (filter.awaitingMore()) {
                predicates.add(cb.equal(order.get("shipmentMode"), ShipmentMode.MULTIPLE));
                predicates.add(cb.isFalse(order.get("shipmentsComplete")));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    /** Экранирует \, % и _ — они в строке поиска обычные символы, а не шаблон LIKE (escape-символ \). */
    public static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private ShipmentSpecifications() {
    }
}
