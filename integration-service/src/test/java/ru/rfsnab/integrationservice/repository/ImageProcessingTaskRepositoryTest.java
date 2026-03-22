package ru.rfsnab.integrationservice.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import ru.rfsnab.integrationservice.BaseIntegrationTest;
import ru.rfsnab.integrationservice.model.ImageProcessingTask;
import ru.rfsnab.integrationservice.model.ImageProcessingTask.TaskStatus;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ImageProcessingTaskRepository")
class ImageProcessingTaskRepositoryTest extends BaseIntegrationTest {

    @Autowired
    private ImageProcessingTaskRepository taskRepository;

    @BeforeEach
    void cleanup() {
        taskRepository.deleteAll();
    }

    private ImageProcessingTask saveTask(String productExternalId, TaskStatus status) {
        return taskRepository.save(ImageProcessingTask.builder()
                .productExternalId(productExternalId)
                .filePath("/tmp/test/" + productExternalId + ".jpg")
                .originalFilename(productExternalId + ".jpg")
                .status(status)
                .sessionId("session-repo-test")
                .build());
    }

    @Test
    @DisplayName("findPendingTasksForProcessing — возвращает только PENDING задачи")
    @Transactional
    void shouldFindOnlyPendingTasks() {
        saveTask("ext-p1", TaskStatus.PENDING);
        saveTask("ext-p2", TaskStatus.PENDING);
        saveTask("ext-c1", TaskStatus.COMPLETED);
        saveTask("ext-f1", TaskStatus.FAILED);
        saveTask("ext-pr1", TaskStatus.PROCESSING);

        List<ImageProcessingTask> pending = taskRepository.findPendingTasksForProcessing(10);

        assertThat(pending).hasSize(2);
        assertThat(pending).allMatch(t -> t.getStatus() == TaskStatus.PENDING);
    }

    @Test
    @DisplayName("findPendingTasksForProcessing — ограничивает по batchSize")
    @Transactional
    void shouldLimitByBatchSize() {
        for (int i = 0; i < 10; i++) {
            saveTask("ext-batch-" + i, TaskStatus.PENDING);
        }

        List<ImageProcessingTask> pending = taskRepository.findPendingTasksForProcessing(3);

        assertThat(pending).hasSize(3);
    }

    @Test
    @DisplayName("claimTask — захватывает PENDING задачу")
    @Transactional
    void shouldClaimPendingTask() {
        ImageProcessingTask task = saveTask("ext-claim-1", TaskStatus.PENDING);

        int claimed = taskRepository.claimTask(task.getId());

        assertThat(claimed).isEqualTo(1);
    }

    @Test
    @DisplayName("claimTask — не захватывает уже PROCESSING задачу")
    @Transactional
    void shouldNotClaimProcessingTask() {
        ImageProcessingTask task = saveTask("ext-claim-2", TaskStatus.PROCESSING);

        int claimed = taskRepository.claimTask(task.getId());

        assertThat(claimed).isEqualTo(0);
    }

    @Test
    @DisplayName("countByStatus — считает по статусу")
    void shouldCountByStatus() {
        saveTask("ext-cnt-1", TaskStatus.PENDING);
        saveTask("ext-cnt-2", TaskStatus.PENDING);
        saveTask("ext-cnt-3", TaskStatus.COMPLETED);

        assertThat(taskRepository.countByStatus(TaskStatus.PENDING)).isEqualTo(2);
        assertThat(taskRepository.countByStatus(TaskStatus.COMPLETED)).isEqualTo(1);
        assertThat(taskRepository.countByStatus(TaskStatus.FAILED)).isEqualTo(0);
    }

    @Test
    @DisplayName("findBySessionId — фильтрует по сессии")
    void shouldFindBySessionId() {
        saveTask("ext-s1", TaskStatus.PENDING);
        
        ImageProcessingTask otherSession = ImageProcessingTask.builder()
                .productExternalId("ext-s2")
                .filePath("/tmp/other.jpg")
                .originalFilename("other.jpg")
                .sessionId("other-session")
                .build();
        taskRepository.save(otherSession);

        List<ImageProcessingTask> result = taskRepository.findBySessionId("session-repo-test");

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getProductExternalId()).isEqualTo("ext-s1");
    }
}
