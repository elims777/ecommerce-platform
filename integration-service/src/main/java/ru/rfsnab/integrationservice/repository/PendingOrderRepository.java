package ru.rfsnab.integrationservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.rfsnab.integrationservice.model.PendingOrder;

import java.util.List;
import java.util.Optional;

@Repository
public interface PendingOrderRepository extends JpaRepository<PendingOrder, Long> {

    /** Непереданные заказы для выгрузки в 1С */
    List<PendingOrder> findByExportedFalseOrderByCreatedAtAsc();

    /** Поиск по orderId (upsert при повторной отправке заказа) */
    Optional<PendingOrder> findByOrderId(String orderId);
}
