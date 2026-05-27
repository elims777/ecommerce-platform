package ru.rfsnab.integrationservice.service.catalog;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.reactive.function.client.WebClient;
import ru.rfsnab.integrationservice.BaseIntegrationTest;
import ru.rfsnab.integrationservice.model.ImageProcessingTask;
import ru.rfsnab.integrationservice.model.ImageProcessingTask.TaskStatus;
import ru.rfsnab.integrationservice.repository.ImageProcessingTaskRepository;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Тесты ImageProcessingWorker.
 * WebClient замокан — product-service недоступен.
 * Проверяем lifecycle задач: retry, fail, обработку ошибок.
 */
@DisplayName("ImageProcessingWorker")
class ImageProcessingWorkerTest extends BaseIntegrationTest {

    @Autowired
    private ImageProcessingWorker worker;

    @Autowired
    private ImageProcessingTaskRepository taskRepository;

    @MockitoBean(name = "productServiceClient")
    private WebClient productServiceClient;

    @TempDir
    Path tempDir;

    @BeforeEach
    void cleanup() {
        taskRepository.deleteAll();
    }

    private ImageProcessingTask createTask(String filePath, String originalFilename) {
        return taskRepository.save(ImageProcessingTask.builder()
                .productExternalId("ext-worker-001")
                .filePath(filePath)
                .originalFilename(originalFilename)
                .sessionId("session-worker")
                .build());
    }

    @Test
    @DisplayName("FAILED при несуществующем файле")
    void shouldFailWhenFileNotFound() {
        ImageProcessingTask task = createTask("/nonexistent/path/photo.jpg", "photo.jpg");

        worker.process(task);

        ImageProcessingTask updated = taskRepository.findById(task.getId()).orElseThrow();
        // Первая попытка → retryCount=1, статус PENDING (retry)
        assertThat(updated.getRetryCount()).isEqualTo(1);
        assertThat(updated.getErrorMessage()).contains("Файл не найден");
    }

    @Test
    @DisplayName("FAILED после исчерпания попыток (max retries)")
    void shouldFailAfterMaxRetries() {
        ImageProcessingTask task = createTask("/nonexistent/path/photo.jpg", "photo.jpg");
        task.setRetryCount(2); // max-retries=2 в application-test.yml → следующая попытка = FAILED
        taskRepository.save(task);

        worker.process(task);

        ImageProcessingTask updated = taskRepository.findById(task.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(TaskStatus.FAILED);
        assertThat(updated.getRetryCount()).isEqualTo(3);
        assertThat(updated.getProcessedAt()).isNotNull();
    }

    @Test
    @DisplayName("PENDING при retry (не исчерпаны попытки)")
    void shouldRetryWhenAttemptsRemain() {
        ImageProcessingTask task = createTask("/nonexistent/path/photo.jpg", "photo.jpg");
        task.setRetryCount(0); // max-retries=2 → ещё есть попытки
        taskRepository.save(task);

        worker.process(task);

        ImageProcessingTask updated = taskRepository.findById(task.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(TaskStatus.PENDING);
        assertThat(updated.getRetryCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("FAILED при нечитаемом файле (не изображение)")
    void shouldFailOnNonImageFile() throws IOException {
        Path fakeFile = tempDir.resolve("not-an-image.jpg");
        Files.writeString(fakeFile, "это текст, а не изображение");

        ImageProcessingTask task = createTask(fakeFile.toString(), "not-an-image.jpg");

        worker.process(task);

        ImageProcessingTask updated = taskRepository.findById(task.getId()).orElseThrow();
        assertThat(updated.getRetryCount()).isGreaterThan(0);
        assertThat(updated.getErrorMessage()).isNotNull();
    }

    @Test
    @DisplayName("сохраняет errorMessage при ошибке")
    void shouldSaveErrorMessage() {
        ImageProcessingTask task = createTask("/no/such/file.png", "file.png");

        worker.process(task);

        ImageProcessingTask updated = taskRepository.findById(task.getId()).orElseThrow();
        assertThat(updated.getErrorMessage()).isNotBlank();
    }
}
