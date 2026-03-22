package ru.rfsnab.integrationservice.service.catalog;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.rfsnab.integrationservice.config.IntegrationProperties;
import ru.rfsnab.integrationservice.model.ImageProcessingTask;
import ru.rfsnab.integrationservice.model.ImageProcessingTask.TaskStatus;
import ru.rfsnab.integrationservice.repository.ImageProcessingTaskRepository;

import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Адаптивный пул потоков для обработки изображений.
 *
 * Архитектура (bounded buffer + adaptive pool):
 * - ThreadPoolExecutor с core=minThreads, max=maxThreads
 * - LinkedBlockingQueue с ограниченной ёмкостью (bounded buffer)
 * - Scheduled задача каждые N мс:
 *   a) подтягивает PENDING-задачи из БД в пул
 *   b) корректирует размер пула по нагрузке (CPU + queue depth)
 *
 * Почему ThreadPoolExecutor, а не virtual threads:
 * В отличие от HTTP-вызовов в CatalogImportService (I/O-bound),
 * обработка изображений — CPU-bound (resize, encode WebP).
 * Platform threads эффективнее для CPU-задач, а ThreadPoolExecutor
 * даёт контроль над concurrency и backpressure через bounded queue.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ImageProcessingPool {

    private final ImageProcessingWorker worker;
    private final ImageProcessingTaskRepository taskRepository;
    private final IntegrationProperties properties;

    private ThreadPoolExecutor executor;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final OperatingSystemMXBean osMxBean = ManagementFactory.getOperatingSystemMXBean();

    @PostConstruct
    void init() {
        IntegrationProperties.ImageProcessingProperties config = properties.getImageProcessing();

        executor = new ThreadPoolExecutor(
                config.getMinThreads(),                     // core: 2
                config.getMaxThreads(),                     // max: 20
                60L, TimeUnit.SECONDS,                      // idle threads живут 60 сек
                new LinkedBlockingQueue<>(config.getBufferMaxSize()),  // bounded buffer: 500
                Thread.ofPlatform()
                        .name("img-proc-", 0)
                        .factory(),
                new ThreadPoolExecutor.CallerRunsPolicy()   // backpressure: при переполнении — caller обрабатывает сам
        );
        executor.allowCoreThreadTimeOut(true);  // core-потоки тоже завершаются при простое

        running.set(true);
        log.info("ImageProcessingPool инициализирован: core={}, max={}, queue={}",
                config.getMinThreads(), config.getMaxThreads(), config.getBufferMaxSize());
    }

    @PreDestroy
    void shutdown() {
        running.set(false);
        executor.shutdown();
        try {
            if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                log.warn("ImageProcessingPool: принудительное завершение после 30 сек");
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
        }
        log.info("ImageProcessingPool остановлен");
    }

    // ==================== Scheduled: Poll + Adapt ====================

    /**
     * Периодически:
     * 1. Забираем PENDING-задачи из БД (SELECT ... FOR UPDATE SKIP LOCKED)
     * 2. Отправляем в пул на обработку
     * 3. Адаптируем размер пула
     *
     * fixedDelay — следующий запуск через N мс ПОСЛЕ завершения текущего,
     * не накапливаются при долгом выполнении.
     */
    @Scheduled(fixedDelayString = "${integration.image-processing.poll-interval-ms:5000}")
    @Transactional
    public void pollAndProcess() {
        if (!running.get()) return;

        int availableSlots = executor.getMaximumPoolSize() - executor.getActiveCount()
                + remainingQueueCapacity();
        if (availableSlots <= 0) {
            log.debug("Пул и очередь заполнены — пропускаем poll");
            return;
        }

        int batchSize = Math.min(availableSlots, properties.getImageProcessing().getPollBatchSize());
        List<ImageProcessingTask> tasks = taskRepository.findPendingTasksForProcessing(batchSize);

        if (tasks.isEmpty()) return;

        int submitted = 0;
        for (ImageProcessingTask task : tasks) {
            // Проверяем что файл ещё существует (мог быть удалён при очистке)
            if (!Files.exists(Path.of(task.getFilePath()))) {
                task.setStatus(TaskStatus.FAILED);
                task.setErrorMessage("Файл не найден на диске: " + task.getFilePath());
                taskRepository.save(task);
                continue;
            }

            // Атомарно захватываем задачу (PENDING → PROCESSING)
            int claimed = taskRepository.claimTask(task.getId());
            if (claimed == 0) continue;  // уже захвачена другим потоком

            task.setStatus(TaskStatus.PROCESSING);
            executor.submit(() -> worker.process(task));
            submitted++;
        }

        if (submitted > 0) {
            log.info("Отправлено {} задач на обработку. Активных потоков: {}, в очереди: {}",
                    submitted, executor.getActiveCount(), executor.getQueue().size());
        }

        adaptPoolSize();
    }

    // ==================== Adaptive Scaling ====================

    /**
     * Динамическая корректировка corePoolSize на основе:
     * - Загрузки CPU (system load average)
     * - Глубины очереди (queue depth)
     *
     * Логика:
     * - Очередь > scaleUpThreshold и CPU < 80% → scale up (+2 потока)
     * - Очередь < scaleDownThreshold и нет активных → scale down (до minThreads)
     */
    private void adaptPoolSize() {
        IntegrationProperties.ImageProcessingProperties config = properties.getImageProcessing();
        int currentCore = executor.getCorePoolSize();
        int queueSize = executor.getQueue().size();
        int activeCount = executor.getActiveCount();

        double cpuLoad = getSystemCpuLoad();

        int newCore = currentCore;

        if (queueSize > config.getScaleUpThreshold() && cpuLoad < 0.8
                && currentCore < config.getMaxThreads()) {
            // Scale up: очередь растёт, CPU справляется
            newCore = Math.min(currentCore + 2, config.getMaxThreads());
        } else if (queueSize < config.getScaleDownThreshold() && activeCount == 0
                && currentCore > config.getMinThreads()) {
            // Scale down: мало задач, потоки простаивают
            newCore = config.getMinThreads();
        }

        if (newCore != currentCore) {
            executor.setCorePoolSize(newCore);
            log.info("Пул адаптирован: {} → {} потоков (CPU: {}%, очередь: {})",
                    currentCore, newCore, Math.round(cpuLoad * 100), queueSize);
        }
    }

    /**
     * Системная загрузка CPU. getSystemLoadAverage() — значение за последнюю минуту.
     * Нормализуем по количеству процессоров: load / cores → 0.0–1.0+
     */
    private double getSystemCpuLoad() {
        double loadAverage = osMxBean.getSystemLoadAverage();
        if (loadAverage < 0) return 0.5;  // не поддерживается — безопасное значение
        int processors = Runtime.getRuntime().availableProcessors();
        return loadAverage / processors;
    }

    private int remainingQueueCapacity() {
        return executor.getQueue().remainingCapacity();
    }

    // ==================== Public API ====================

    /**
     * Создаёт задачи на обработку изображений из данных import.xml.
     * Вызывается из CatalogImportService после успешного импорта товаров.
     *
     * @param exchangeDir директория обмена (корень для относительных путей картинок)
     * @param productExternalId externalId товара
     * @param imagePaths список относительных путей к картинкам из XML (тег Картинка)
     * @param sessionId ID сессии обмена
     */
    public void enqueueImages(Path exchangeDir, String productExternalId,
                              List<String> imagePaths, String sessionId) {
        for (String relativePath : imagePaths) {
            Path absolutePath = exchangeDir.resolve(relativePath).normalize();

            // Защита от path traversal
            if (!absolutePath.startsWith(exchangeDir)) {
                log.warn("Path traversal attempt: {} (session: {})", relativePath, sessionId);
                continue;
            }

            if (!Files.exists(absolutePath)) {
                log.warn("Файл изображения не найден: {} (product: {}, session: {})",
                        absolutePath, productExternalId, sessionId);
                continue;
            }

            ImageProcessingTask task = ImageProcessingTask.builder()
                    .productExternalId(productExternalId)
                    .filePath(absolutePath.toString())
                    .originalFilename(relativePath)
                    .sessionId(sessionId)
                    .build();

            taskRepository.save(task);
        }
    }

    /**
     * Количество задач в статусе PENDING — для мониторинга.
     */
    public long getPendingCount() {
        return taskRepository.countByStatus(TaskStatus.PENDING);
    }

    /**
     * Статистика пула для мониторинга / отладки.
     */
    public PoolStats getStats() {
        return new PoolStats(
                executor.getCorePoolSize(),
                executor.getMaximumPoolSize(),
                executor.getActiveCount(),
                executor.getQueue().size(),
                executor.getCompletedTaskCount(),
                taskRepository.countByStatus(TaskStatus.PENDING),
                taskRepository.countByStatus(TaskStatus.FAILED)
        );
    }

    public record PoolStats(
            int coreSize,
            int maxSize,
            int activeThreads,
            int queueSize,
            long completedTasks,
            long pendingInDb,
            long failedInDb
    ) {}
}