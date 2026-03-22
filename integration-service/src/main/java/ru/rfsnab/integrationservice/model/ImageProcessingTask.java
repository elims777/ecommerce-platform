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
 * Задача на обработку изображения товара.
 * Создаётся при парсинге import.xml, обрабатывается адаптивным пулом.
 * Жизненный цикл: PENDING → PROCESSING → COMPLETED / FAILED
 * При FAILED и retryCount < maxRetries — возвращается в PENDING.
 */
@Entity
@Table(name = "image_processing_tasks")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImageProcessingTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** ExternalId товара в 1С (UUID) — для отправки в product-service */
    @Column(name = "product_external_id", nullable = false, length = 50)
    private String productExternalId;

    /** Абсолютный путь к файлу на диске (exchangeDir + relative path из XML) */
    @Column(name = "file_path", nullable = false, length = 500)
    private String filePath;

    /** Оригинальное имя файла из тега Картинка (например "foto/product123.jpg") */
    @Column(name = "original_filename", nullable = false, length = 255)
    private String originalFilename;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private TaskStatus status = TaskStatus.PENDING;

    @Column(name = "retry_count", nullable = false)
    @Builder.Default
    private int retryCount = 0;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    /** ID сессии обмена (V2 миграция) */
    @Column(name = "session_id", length = 64)
    private String sessionId;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    public enum TaskStatus {
        PENDING,
        PROCESSING,
        COMPLETED,
        FAILED
    }
}