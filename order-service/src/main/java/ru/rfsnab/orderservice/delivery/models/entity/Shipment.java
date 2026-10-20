package ru.rfsnab.orderservice.delivery.models.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import ru.rfsnab.orderservice.delivery.models.entity.enums.CarrierCode;
import ru.rfsnab.orderservice.delivery.models.entity.enums.ShipmentDirection;
import ru.rfsnab.orderservice.delivery.models.entity.enums.ShipmentSource;
import ru.rfsnab.orderservice.delivery.models.entity.enums.ShipmentStatusCode;
import ru.rfsnab.orderservice.models.entity.Order;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

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

    @Column(name = "sender_inn", length = 12)
    private String senderInn;

    private String receiver;

    @Column(name = "receiver_inn", length = 12)
    private String receiverInn;

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

    @Column(name = "weight_kg", precision = 12, scale = 3)
    private BigDecimal weightKg;

    @Column(name = "volume_m3", precision = 12, scale = 4)
    private BigDecimal volumeM3;

    @Column(name = "places_count")
    private Integer placesCount;

    @Column(precision = 12, scale = 2)
    private BigDecimal cost;

    /** null — ТК не сообщает. */
    private Boolean paid;

    /** true — забор от адреса, false — от терминала/ПВЗ. */
    @Column(name = "from_address")
    private Boolean fromAddress;

    /** true — доставка до адреса, false — до терминала/ПВЗ. */
    @Column(name = "to_address")
    private Boolean toAddress;

    /** Фактическая выдача/доставка. */
    @Column(name = "delivered_at")
    private Instant deliveredAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<ShipmentDocument> documents;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<ShipmentPackage> packages;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<ShipmentPayer> payers;
}
