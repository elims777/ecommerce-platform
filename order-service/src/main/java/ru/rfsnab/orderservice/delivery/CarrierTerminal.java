package ru.rfsnab.orderservice.delivery;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

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

    /**
     * Отличается ли содержимое от другой точки (без id, active и syncedAt).
     */
    public boolean differsFrom(CarrierTerminal other) {
        return pointType != other.pointType
                || !Objects.equals(city, other.city)
                || !Objects.equals(name, other.name)
                || !Objects.equals(address, other.address)
                || latitude.compareTo(other.latitude) != 0
                || longitude.compareTo(other.longitude) != 0
                || !Objects.equals(workingHours, other.workingHours)
                || !Objects.equals(phone, other.phone);
    }

    /**
     * Копирует содержимое другой точки (без id, active и syncedAt).
     */
    public void copyFrom(CarrierTerminal other) {
        pointType = other.pointType;
        city = other.city;
        name = other.name;
        address = other.address;
        latitude = other.latitude;
        longitude = other.longitude;
        workingHours = other.workingHours;
        phone = other.phone;
    }
}
