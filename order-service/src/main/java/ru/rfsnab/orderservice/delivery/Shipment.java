package ru.rfsnab.orderservice.delivery;

import jakarta.persistence.*;
import lombok.*;
import ru.rfsnab.orderservice.models.entity.Order;

import java.time.Instant;

/**
 * Отправление (накладная ТК). Не зависит от заказов: в журнале есть и отправления, созданные не через сайт.
 */
@Entity
@Table(name = "shipments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Shipment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    private Order order;

    @Enumerated(EnumType.STRING)
    @Column(name = "carrier_code", nullable = false, length = 20)
    private CarrierCode carrierCode;

    @Column(name = "waybill_number", nullable = false, length = 100)
    private String waybillNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ShipmentDirection direction;

    private String sender;

    private String receiver;

    @Column(name = "route_from")
    private String routeFrom;

    @Column(name = "route_to")
    private String routeTo;

    @Column(name = "shipped_at")
    private Instant shippedAt;

    @Column(name = "estimated_delivery_at")
    private Instant estimatedDeliveryAt;

    /** Сырой текст статуса ТК. */
    @Column(name = "carrier_status")
    private String carrierStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "carrier_status_code", length = 30)
    private ShipmentStatusCode carrierStatusCode;

    /** Когда статус последний раз изменился. */
    @Column(name = "carrier_status_updated_at", nullable = false)
    private Instant carrierStatusUpdatedAt;

    @Column(name = "polled_at")
    private Instant polledAt;

    /** «Нет движения»: статус не менялся слишком долго, опрос остановлен. */
    @Column(name = "polling_stopped", nullable = false)
    private boolean pollingStopped;

    @Column(name = "not_found_since")
    private Instant notFoundSince;

    @Column(name = "not_found_notified", nullable = false)
    private boolean notFoundNotified;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ShipmentSource source;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
