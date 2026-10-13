package ru.rfsnab.orderservice.delivery;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.rfsnab.orderservice.models.entity.enums.OrderStatus;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ShipmentRepository extends JpaRepository<Shipment, Long>, JpaSpecificationExecutor<Shipment> {

    /** Отправления заказов одним запросом (с заказом — без N+1 при сборке DTO). */
    @Query("SELECT s FROM Shipment s JOIN FETCH s.order o WHERE o.id IN :orderIds ORDER BY s.createdAt, s.id")
    List<Shipment> findAllByOrderIds(@Param("orderIds") Collection<UUID> orderIds);

    /** Отправление с заказом (заказ может быть null). */
    @Query("SELECT s FROM Shipment s LEFT JOIN FETCH s.order WHERE s.id = :id")
    Optional<Shipment> findWithOrderById(@Param("id") Long id);

    /** Отправление по ключу накладной. */
    @Query("SELECT s FROM Shipment s LEFT JOIN FETCH s.order WHERE s.carrierCode = :carrier AND s.waybillNumber = :waybill")
    Optional<Shipment> findByCarrierAndWaybill(@Param("carrier") CarrierCode carrier, @Param("waybill") String waybill);

    boolean existsByCarrierCodeAndWaybillNumber(CarrierCode carrierCode, String waybillNumber);

    long countByOrderId(UUID orderId);

    /**
     * Отправления ТК, которые нужно опрашивать: не остановлены, не в финальном статусе,
     * заказ (если есть) не завершён и не отменён.
     */
    @Query("""
            SELECT s FROM Shipment s LEFT JOIN FETCH s.order o
            WHERE s.carrierCode = :carrier AND s.pollingStopped = false
              AND (s.carrierStatusCode IS NULL OR s.carrierStatusCode NOT IN :finalCodes)
              AND (s.order IS NULL OR o.status NOT IN :finishedOrderStatuses)
            ORDER BY s.id
            """)
    List<Shipment> findPollable(@Param("carrier") CarrierCode carrier,
                                @Param("finalCodes") Collection<ShipmentStatusCode> finalCodes,
                                @Param("finishedOrderStatuses") Collection<OrderStatus> finishedOrderStatuses);
}
