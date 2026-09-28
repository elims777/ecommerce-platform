package ru.rfsnab.orderservice.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ru.rfsnab.orderservice.models.entity.Order;
import ru.rfsnab.orderservice.models.entity.enums.OrderStatus;
import ru.rfsnab.orderservice.repository.projection.OrderStatsProjection;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OrderRepository extends JpaRepository<Order, UUID> {

    @EntityGraph(attributePaths = "items")
    Optional<Order> findByOrderNumber(String orderNumber);

    Page<Order> findByUserId(Long userId, Pageable pageable);

    List<Order> findByUserIdAndStatus(Long userId, OrderStatus status);

    boolean existsByOrderNumber(String orderNumber);

    long countByUserId(Long userId);

    long countByUserIdAndStatusNotIn(Long userId, List<OrderStatus> excludedStatuses);

    long countByInnAndStatusNotIn(String inn, List<OrderStatus> excludedStatuses);

    Page<Order> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    Page<Order> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Page<Order> findByStatusOrderByCreatedAtDesc(OrderStatus status, Pageable pageable);

    @EntityGraph(attributePaths = "items")
    Optional<Order> findWithItemsById(UUID id);

    @Query("SELECT o FROM Order o WHERE o.createdAt >= :from AND o.createdAt <= :to ORDER BY o.createdAt DESC")
    Page<Order> findByCreatedAtBetween(
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            Pageable pageable
    );

    /**
     * Агрегаты заказов для сводки админки — один проход по таблице.
     * Наборы статусов передаются параметрами, чтобы не хардкодить строки в JPQL.
     */
    @Query("""
            SELECT COUNT(o) AS total,
                   COALESCE(SUM(CASE WHEN o.status NOT IN :finalStatuses THEN 1 ELSE 0 END), 0) AS inProgress,
                   COALESCE(SUM(CASE WHEN o.status IN :completedStatuses THEN 1 ELSE 0 END), 0) AS completed,
                   COALESCE(SUM(CASE WHEN o.status IN :cancelledStatuses THEN 1 ELSE 0 END), 0) AS cancelled,
                   COALESCE(SUM(CASE WHEN o.createdAt >= :since THEN 1 ELSE 0 END), 0) AS newLast30Days
            FROM Order o
            """)
    OrderStatsProjection fetchOrderStats(
            @Param("finalStatuses") List<OrderStatus> finalStatuses,
            @Param("completedStatuses") List<OrderStatus> completedStatuses,
            @Param("cancelledStatuses") List<OrderStatus> cancelledStatuses,
            @Param("since") LocalDateTime since
    );
}
