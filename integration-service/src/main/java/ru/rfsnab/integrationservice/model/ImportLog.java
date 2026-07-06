package ru.rfsnab.integrationservice.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Лог импорта — фиксирует результат каждого запуска обмена.
 * Колонки из V1: exchange_type, total_received, created, updated, failed, duration_ms, error_message, created_at.
 * Колонки из V2: session_id, status, started_at, completed_at.
 * unchanged — количество записей без изменений значимых полей (честный счётчик "обновлено").
 */
@Entity
@Table(name = "import_log")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImportLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Тип обмена: CATALOG, OFFERS, ORDER_STATUS */
    @Column(name = "exchange_type", nullable = false, length = 20)
    private String exchangeType;

    /** ID сессии обмена (V2) */
    @Column(name = "session_id", length = 64)
    private String sessionId;

    /** Статус импорта: SUCCESS, PARTIAL, FAILED (V2) */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private ImportStatus status = ImportStatus.SUCCESS;

    @Column(name = "total_received", nullable = false)
    @Builder.Default
    private int totalReceived = 0;

    /** Количество созданных записей */
    @Column(name = "created", nullable = false)
    @Builder.Default
    private int created = 0;

    /** Количество обновлённых записей */
    @Column(name = "updated", nullable = false)
    @Builder.Default
    private int updated = 0;

    /** Количество записей без изменений значимых полей (не считаются как updated) */
    @Column(name = "unchanged", nullable = false)
    @Builder.Default
    private int unchanged = 0;

    /** Количество ошибок */
    @Column(name = "failed", nullable = false)
    @Builder.Default
    private int failed = 0;

    /** Количество обработанных изображений */
    @Column(name = "images_processed", nullable = false)
    @Builder.Default
    private int imagesProcessed = 0;

    /** Количество изображений с ошибкой */
    @Column(name = "images_failed", nullable = false)
    @Builder.Default
    private int imagesFailed = 0;

    /** Длительность импорта в миллисекундах */
    @Column(name = "duration_ms")
    private Long durationMs;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    /** Время начала импорта (V2) */
    @Column(name = "started_at")
    private LocalDateTime startedAt;

    /** Время завершения импорта (V2) */
    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    /** Номер попытки автовозобновления (0 — обычный запуск) */
    @Column(name = "resume_attempts", nullable = false)
    @Builder.Default
    private int resumeAttempts = 0;

    public enum ImportStatus {
        SUCCESS,
        PARTIAL,
        FAILED,
        /** Импорт выполняется; остаётся в БД после краша JVM — признак незавершённого импорта */
        IN_PROGRESS,
        /** Импорт был прерван рестартом сервиса (выставляется при старте вместо IN_PROGRESS) */
        INTERRUPTED
    }
}