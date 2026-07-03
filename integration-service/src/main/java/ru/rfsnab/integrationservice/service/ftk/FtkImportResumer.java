package ru.rfsnab.integrationservice.service.ftk;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import ru.rfsnab.integrationservice.model.ImportLog;
import ru.rfsnab.integrationservice.model.ImportLog.ImportStatus;
import ru.rfsnab.integrationservice.repository.ImportLogRepository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Автовозобновление ФТК-импорта после рестарта сервиса.
 *
 * Запись import_log со статусом IN_PROGRESS на старте приложения означает,
 * что предыдущий импорт был прерван (краш JVM, рестарт контейнера): при штатном
 * завершении статус всегда финализируется. Такие записи помечаются INTERRUPTED,
 * и импорт запускается заново — это безопасно: апсерт товаров идемпотентен,
 * уже загруженные картинки пропускаются по сверке fileKey.
 *
 * Лимит попыток защищает от бесконечного цикла, если краш воспроизводится
 * на каждом прогоне (как SIGSEGV на WebP-картинках 02-03.07.2026).
 *
 * В норме незавершённая запись максимум одна (AtomicBoolean в FtkImportService
 * не допускает параллельных импортов); несколько IN_PROGRESS — патологический
 * случай, все помечаются INTERRUPTED и схлопываются в одно возобновление
 * с максимальным счётчиком попыток.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class FtkImportResumer {

    static final int MAX_RESUME_ATTEMPTS = 3;

    private final ImportLogRepository importLogRepository;
    private final FtkImportService importService;

    @EventListener(ApplicationReadyEvent.class)
    public void resumeUnfinishedImport() {
        List<ImportLog> unfinished = importLogRepository.findByExchangeTypeAndStatus(
                FtkImportService.EXCHANGE_TYPE_FTK, ImportStatus.IN_PROGRESS);
        if (unfinished.isEmpty()) return;

        int maxAttempts = 0;
        for (ImportLog entry : unfinished) {
            entry.setStatus(ImportStatus.INTERRUPTED);
            entry.setErrorMessage("Импорт прерван перезапуском сервиса");
            entry.setCompletedAt(LocalDateTime.now());
            maxAttempts = Math.max(maxAttempts, entry.getResumeAttempts());
        }
        importLogRepository.saveAll(unfinished);

        if (maxAttempts >= MAX_RESUME_ATTEMPTS) {
            log.error("ФТК-импорт прерван {} раз подряд — автовозобновление остановлено, требуется ручной запуск",
                    maxAttempts + 1);
            return;
        }

        int attempt = maxAttempts + 1;
        log.warn("Обнаружен незавершённый ФТК-импорт — автовозобновление, попытка {}/{}", attempt, MAX_RESUME_ATTEMPTS);
        importService.resumeImportFromFtp(attempt);
    }
}
