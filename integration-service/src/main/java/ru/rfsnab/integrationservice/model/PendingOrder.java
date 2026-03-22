package ru.rfsnab.integrationservice.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Заказ в очереди на выгрузку в 1С.
 * Заполняется Kafka consumer'ом, отдаётся при mode=query.
 */
@Entity
@Table(name = "pending_orders")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PendingOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** UUID заказа из order-service (строка, т.к. приходит из Kafka) */
    @Column(name = "order_id", nullable = false)
    private String orderId;

    /** Номер заказа (orderNumber) — для справки */
    @Column(name = "order_external_id", length = 50)
    private String externalId;

    /** Полные данные заказа в JSON (Order1CExportEvent as-is из Kafka) */
    @Column(name = "order_data", nullable = false, columnDefinition = "JSONB")
    private String orderData;

    /** Уже передан в 1С */
    @Column(name = "exported", nullable = false)
    @Builder.Default
    private boolean exported = false;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "exported_at")
    private LocalDateTime exportedAt;
}
