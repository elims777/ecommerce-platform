package ru.rfsnab.integrationservice.service.catalog;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.coobird.thumbnailator.Thumbnails;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import ru.rfsnab.integrationservice.config.IntegrationProperties;
import ru.rfsnab.integrationservice.model.ImageProcessingTask;
import ru.rfsnab.integrationservice.model.ImageProcessingTask.TaskStatus;
import ru.rfsnab.integrationservice.repository.ImageProcessingTaskRepository;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;

/**
 * Обработчик одной задачи обработки изображения.
 *
 * Pipeline: читаем файл с диска → resize (max 1200px) → конвертация в WebP (quality 80%)
 * → multipart POST в product-service /api/v1/products/external/{externalId}/images
 * → удаляем оригинал с диска.
 *
 * Thumbnailator используется для resize — быстрая и проверенная библиотека.
 * WebP-конвертация через ImageIO + webp-imageio (уже в зависимостях pom.xml).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ImageProcessingWorker {

    private static final String UPLOAD_URI_TEMPLATE = "/api/v1/products/external/{externalId}/images";
    private static final String WEBP_FORMAT = "webp";

    private final WebClient productServiceClient;
    private final IntegrationProperties properties;
    private final ImageProcessingTaskRepository taskRepository;

    /**
     * Обрабатывает одну задачу. Вызывается из пула потоков ImageProcessingPool.
     * Метод сам управляет статусами задачи и retry-логикой.
     */
    public void process(ImageProcessingTask task) {
        log.debug("Обработка изображения: taskId={}, product={}, file={}",
                task.getId(), task.getProductExternalId(), task.getOriginalFilename());
        try {
            Path sourceFile = Path.of(task.getFilePath());
            validateSourceFile(sourceFile);

            // 1. Resize + конвертация в WebP
            byte[] webpBytes = convertToWebP(sourceFile);

            // 2. Отправка в product-service через multipart
            String webpFileName = buildWebpFileName(task.getOriginalFilename());
            uploadToProductService(task.getProductExternalId(), webpBytes, webpFileName);

            // 3. Удаление оригинала
            deleteOriginal(sourceFile);

            // 4. Успех
            completeTask(task);
            log.debug("Изображение обработано: taskId={}, размер WebP={}KB",
                    task.getId(), webpBytes.length / 1024);

        } catch (Exception e) {
            handleFailure(task, e);
        }
    }

    // ==================== Image Processing ====================

    /**
     * Resize + конвертация в WebP.
     *
     * Thumbnailator определяет формат автоматически, ресайзит с сохранением
     * пропорций (aspect ratio). keepAspectRatio(true) по умолчанию.
     * Максимальный размер по большей стороне — из конфигурации (default 1200px).
     * Quality 0.80 для WebP — хороший баланс размер/качество.
     */
    private byte[] convertToWebP(Path sourceFile) throws IOException {
        IntegrationProperties.ImageProcessingProperties config = properties.getImageProcessing();

        BufferedImage original = ImageIO.read(sourceFile.toFile());
        if (original == null) {
            throw new IOException("Не удалось прочитать изображение: " + sourceFile.getFileName());
        }

        int maxDimension = config.getMaxImageWidth();
        double quality = config.getWebpQuality();

        // Если изображение меньше maxDimension — не увеличиваем
        int targetWidth = Math.min(original.getWidth(), maxDimension);
        int targetHeight = Math.min(original.getHeight(), maxDimension);

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            Thumbnails.of(original)
                    .size(targetWidth, targetHeight)
                    .outputFormat(WEBP_FORMAT)
                    .outputQuality(quality)
                    .toOutputStream(baos);

            return baos.toByteArray();
        }
    }

    // ==================== Upload to Product-Service ====================

    /**
     * Отправляет WebP-файл в product-service как multipart/form-data.
     * block() в virtual thread не блокирует platform thread.
     */
    private void uploadToProductService(String externalId, byte[] webpBytes, String fileName) {
        MultipartBodyBuilder bodyBuilder = new MultipartBodyBuilder();
        bodyBuilder.part("file", new ByteArrayResource(webpBytes) {
                    @Override
                    public String getFilename() {
                        return fileName;
                    }
                })
                .contentType(MediaType.valueOf("image/webp"));

        try {
            productServiceClient.post()
                    .uri(UPLOAD_URI_TEMPLATE, externalId)
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(BodyInserters.fromMultipartData(bodyBuilder.build()))
                    .retrieve()
                    .toBodilessEntity()
                    .block();
        } catch (WebClientResponseException e) {
            throw new RuntimeException(
                    "Ошибка загрузки в product-service: HTTP " + e.getStatusCode()
                            + " — " + e.getResponseBodyAsString(), e);
        }
    }

    // ==================== Task Lifecycle ====================

    private void completeTask(ImageProcessingTask task) {
        task.setStatus(TaskStatus.COMPLETED);
        task.setProcessedAt(LocalDateTime.now());
        task.setErrorMessage(null);
        taskRepository.save(task);
    }

    /**
     * Обработка ошибки: если retryCount < maxRetries — возвращаем в PENDING,
     * иначе — FAILED навсегда.
     */
    private void handleFailure(ImageProcessingTask task, Exception e) {
        int maxRetries = properties.getImageProcessing().getMaxRetries();
        task.setRetryCount(task.getRetryCount() + 1);
        task.setErrorMessage(truncateMessage(e.getMessage(), 500));
        task.setProcessedAt(LocalDateTime.now());

        if (task.getRetryCount() < maxRetries) {
            task.setStatus(TaskStatus.PENDING);
            log.warn("Retry {}/{} для taskId={}: {}", task.getRetryCount(), maxRetries,
                    task.getId(), e.getMessage());
        } else {
            task.setStatus(TaskStatus.FAILED);
            log.error("Задача FAILED после {} попыток: taskId={}, product={}, file={}",
                    maxRetries, task.getId(), task.getProductExternalId(), task.getOriginalFilename(), e);
        }
        taskRepository.save(task);
    }

    // ==================== Utilities ====================

    private void validateSourceFile(Path sourceFile) {
        if (!Files.exists(sourceFile)) {
            throw new IllegalStateException("Файл не найден: " + sourceFile);
        }
        if (!Files.isReadable(sourceFile)) {
            throw new IllegalStateException("Нет доступа к файлу: " + sourceFile);
        }
    }

    private void deleteOriginal(Path sourceFile) {
        try {
            Files.deleteIfExists(sourceFile);
        } catch (IOException e) {
            // Не критично — файл останется на диске, будет удалён при очистке
            log.warn("Не удалось удалить оригинал: {}", sourceFile, e);
        }
    }

    /**
     * Формирует имя WebP-файла из оригинального имени.
     * "import_files/foto/product123.jpg" → "product123.webp"
     */
    private String buildWebpFileName(String originalFilename) {
        String fileName = Path.of(originalFilename).getFileName().toString();
        int dotIndex = fileName.lastIndexOf('.');
        String baseName = (dotIndex > 0) ? fileName.substring(0, dotIndex) : fileName;
        return baseName + ".webp";
    }

    private String truncateMessage(String message, int maxLength) {
        if (message == null) return "Unknown error";
        return message.length() > maxLength ? message.substring(0, maxLength) : message;
    }
}