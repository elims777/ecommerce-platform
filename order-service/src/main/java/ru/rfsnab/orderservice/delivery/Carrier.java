package ru.rfsnab.orderservice.delivery;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Настройки транспортной компании, управляются из админки «Логистика».
 * Строки создаются миграцией — по одной на каждый CarrierCode.
 */
@Entity
@Table(name = "carriers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Carrier {

    @Id
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private CarrierCode code;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "address_delivery_enabled", nullable = false)
    private boolean addressDeliveryEnabled;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    void touch() {
        updatedAt = LocalDateTime.now();
    }
}
