package ru.rfsnab.orderservice.delivery;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
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

    /** Включает админ вручную, автоматически после синхронизации не включается. */
    @Column(name = "terminal_delivery_enabled", nullable = false)
    private boolean terminalDeliveryEnabled;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    /** Последняя успешная синхронизация терминалов. */
    @Column(name = "terminals_synced_at")
    private Instant terminalsSyncedAt;

    /** Итог последней синхронизации — CarrierSyncReport в виде JSON. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "last_sync_report")
    private String lastSyncReport;

    @Column(name = "active_terminals_count", nullable = false)
    private int activeTerminalsCount;

    /** Ссылка «Отследить» с подстановкой {number}; null — ссылки нет. */
    @Column(name = "tracking_url_template", length = 500)
    private String trackingUrlTemplate;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /** Ссылка «Отследить» для накладной; null, если шаблон не задан. */
    public String trackingUrl(String waybillNumber) {
        return trackingUrlTemplate == null ? null : trackingUrlTemplate.replace("{number}", URLEncoder.encode(waybillNumber, StandardCharsets.UTF_8));
    }

    @PrePersist
    @PreUpdate
    void touch() {
        updatedAt = LocalDateTime.now();
    }
}
