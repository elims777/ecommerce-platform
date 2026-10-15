package ru.rfsnab.integrationservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import ru.rfsnab.integrationservice.model.ImageProcessingTask;
import ru.rfsnab.integrationservice.model.ImageProcessingTask.TaskStatus;

import java.util.List;

@Repository
public interface ImageProcessingTaskRepository extends JpaRepository<ImageProcessingTask, Long> {

    /**
     * Забрать batch задач из очереди атомарно.
     * FOR UPDATE SKIP LOCKED — конкурентные потоки не блокируются,
     * а пропускают уже захваченные строки. Ключевой паттерн для work queue на PostgreSQL.
     */
    @Query(value = """
            SELECT * FROM image_processing_tasks
            WHERE status = 'PENDING'
            ORDER BY created_at ASC
            LIMIT :batchSize
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<ImageProcessingTask> findPendingTasksForProcessing(@Param("batchSize") int batchSize);

    /**
     * Атомарно перевести задачу в PROCESSING (проверка статуса на уровне WHERE).
     * Возвращает 1 = захвачена, 0 = уже обрабатывается другим потоком.
     */
    @Modifying
    @Query(value = """
            UPDATE image_processing_tasks
            SET status = 'PROCESSING'
            WHERE id = :taskId AND status = 'PENDING'
            """, nativeQuery = true)
    int claimTask(@Param("taskId") Long taskId);

    /**
     * Вернуть в очередь задачи, зависшие в PROCESSING после падения JVM.
     * Попытка засчитывается (как в ImageProcessingWorker.handleFailure): задача, убивающая JVM,
     * после maxRetries уходит в FAILED, а не зацикливается.
     */
    @Modifying
    @Transactional
    @Query(value = """
            UPDATE image_processing_tasks
            SET retry_count = retry_count + 1,
                status = CASE WHEN retry_count + 1 >= :maxRetries THEN 'FAILED' ELSE 'PENDING' END,
                error_message = 'Прервано перезапуском сервиса'
            WHERE status = 'PROCESSING'
            """, nativeQuery = true)
    int recoverInterruptedTasks(@Param("maxRetries") int maxRetries);

    long countByStatus(TaskStatus status);

    List<ImageProcessingTask> findBySessionId(String sessionId);

    long countBySessionIdAndStatusIn(String sessionId, List<TaskStatus> statuses);

    long countBySessionIdAndStatus(String sessionId, TaskStatus status);
}