package ru.rfsnab.orderservice.delivery.models.entity;

import jakarta.persistence.*;
import lombok.*;
import ru.rfsnab.orderservice.delivery.models.entity.enums.CarrierCode;
import ru.rfsnab.orderservice.delivery.models.entity.enums.PointType;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Точка выдачи ТК (терминал, ПВЗ, постамат), загруженная синхронизацией.
 * Уникальна по (carrierCode, externalId); пропавшие у ТК точки не удаляются, а получают active=false.
 */
@Entity
@Table(name = "carrier_terminals")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CarrierTerminal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "carrier_code", nullable = false, length = 20)
    private CarrierCode carrierCode;

    @Column(name = "external_id", nullable = false)
    private String externalId;

    @Enumerated(EnumType.STRING)
    @Column(name = "point_type", nullable = false, length = 20)
    private PointType pointType;

    @Column(nullable = false)
    private String city;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String address;

    @Column(nullable = false, precision = 9, scale = 6)
    private BigDecimal latitude;

    @Column(nullable = false, precision = 9, scale = 6)
    private BigDecimal longitude;

    @Column(name = "working_hours")
    private String workingHours;

    private String phone;

    @Column(nullable = false)
    private boolean active;

    /** Когда запись последний раз создана или изменена синхронизацией. */
    @Column(name = "synced_at", nullable = false)
    private Instant syncedAt;
}
