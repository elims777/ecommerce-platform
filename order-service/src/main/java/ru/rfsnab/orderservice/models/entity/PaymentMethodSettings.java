package ru.rfsnab.orderservice.models.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "payment_method_settings")
@Getter
@Setter
public class PaymentMethodSettings {

    @Id
    private Long id = 1L;

    @Column(name = "sbp_enabled", nullable = false)
    private boolean sbpEnabled;

    @Column(name = "card_enabled", nullable = false)
    private boolean cardEnabled;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    void touch() {
        updatedAt = LocalDateTime.now();
    }
}
