package ru.rfsnab.integrationservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import ru.rfsnab.integrationservice.model.PendingOrder;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface PendingOrderRepository extends JpaRepository<PendingOrder, Long> {

    /** Непереданные заказы для выгрузки в 1С */
    List<PendingOrder> findByExportedFalseOrderByCreatedAtAsc();

    /** Проверка дубликата (idempotency) */
    boolean existsByOrderId(String orderId);

    /** Пометить все непереданные как переданные */
    @Modifying
    @Query("""
            UPDATE PendingOrder p
            SET p.exported = true, p.exportedAt = :now
            WHERE p.exported = false
            """)
    int markAllAsExported(LocalDateTime now);
}
