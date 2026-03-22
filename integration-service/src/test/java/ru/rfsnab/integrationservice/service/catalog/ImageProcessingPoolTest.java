package ru.rfsnab.integrationservice.service.catalog;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ImageProcessingPool")
class ImageProcessingPoolTest extends BaseIntegrationTest {

    @Autowired
    private ImageProcessingPool imageProcessingPool;

    @Autowired
    private ImageProcessingTaskRepository taskRepository;

    @MockitoBean(name = "productServiceClient")
    private WebClient productServiceClient;

    @TempDir
    Path tempExchangeDir;

    @BeforeEach
    void cleanup() {
        taskRepository.deleteAll();
    }

    @Nested
    @DisplayName("enqueueImages")
    class EnqueueTests {

        @Test
        @DisplayName("создаёт задачи в БД для каждой картинки")
        void shouldCreateTasksInDb() throws IOException {
            // Создаём файлы картинок
            Path imagesDir = tempExchangeDir.resolve("import_files");
            Files.createDirectories(imagesDir);
            Files.writeString(imagesDir.resolve("photo1.jpg"), "fake-image-1");
            Files.writeString(imagesDir.resolve("photo2.jpg"), "fake-image-2");

            List<String> imagePaths = List.of(
                    "import_files/photo1.jpg",
                    "import_files/photo2.jpg"
            );

            imageProcessingPool.enqueueImages(tempExchangeDir, "ext-001", imagePaths, "session-1");

            List<ImageProcessingTask> tasks = taskRepository.findAll();
            assertThat(tasks).hasSize(2);
            assertThat(tasks).allMatch(t -> t.getStatus() == TaskStatus.PENDING);
            assertThat(tasks).allMatch(t -> "ext-001".equals(t.getProductExternalId()));
            assertThat(tasks).allMatch(t -> "session-1".equals(t.getSessionId()));
        }

        @Test
        @DisplayName("пропускает несуществующий файл")
        void shouldSkipNonExistentFile() {
            List<String> imagePaths = List.of("import_files/missing.jpg");

            imageProcessingPool.enqueueImages(tempExchangeDir, "ext-002", imagePaths, "session-2");

            assertThat(taskRepository.findAll()).isEmpty();
        }

        @Test
        @DisplayName("блокирует path traversal")
        void shouldBlockPathTraversal() throws IOException {
            Files.writeString(tempExchangeDir.resolve("legit.jpg"), "ok");

            List<String> imagePaths = List.of("../../etc/passwd");

            imageProcessingPool.enqueueImages(tempExchangeDir, "ext-003", imagePaths, "session-3");

            assertThat(taskRepository.findAll()).isEmpty();
        }
    }

    @Nested
    @DisplayName("getStats / getPendingCount")
    class StatsTests {

        @Test
        @DisplayName("возвращает корректное количество pending задач")
        void shouldReturnPendingCount() throws IOException {
            Path imagesDir = tempExchangeDir.resolve("import_files");
            Files.createDirectories(imagesDir);
            Files.writeString(imagesDir.resolve("p1.jpg"), "img");
            Files.writeString(imagesDir.resolve("p2.jpg"), "img");
            Files.writeString(imagesDir.resolve("p3.jpg"), "img");

            imageProcessingPool.enqueueImages(tempExchangeDir, "ext-010",
                    List.of("import_files/p1.jpg", "import_files/p2.jpg", "import_files/p3.jpg"),
                    "session-10");

            assertThat(imageProcessingPool.getPendingCount()).isEqualTo(3);
        }

        @Test
        @DisplayName("getStats возвращает статистику пула")
        void shouldReturnPoolStats() {
            ImageProcessingPool.PoolStats stats = imageProcessingPool.getStats();

            assertThat(stats.coreSize()).isGreaterThanOrEqualTo(1);
            assertThat(stats.maxSize()).isGreaterThanOrEqualTo(1);
            assertThat(stats.pendingInDb()).isGreaterThanOrEqualTo(0);
        }
    }
}
