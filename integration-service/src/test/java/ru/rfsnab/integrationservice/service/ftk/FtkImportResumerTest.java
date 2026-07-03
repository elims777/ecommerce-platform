package ru.rfsnab.integrationservice.service.ftk;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.rfsnab.integrationservice.model.ImportLog;
import ru.rfsnab.integrationservice.model.ImportLog.ImportStatus;
import ru.rfsnab.integrationservice.repository.ImportLogRepository;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("FtkImportResumer")
@ExtendWith(MockitoExtension.class)
class FtkImportResumerTest {

    @Mock private ImportLogRepository importLogRepository;
    @Mock private FtkImportService importService;

    private FtkImportResumer resumer;

    @BeforeEach
    void setUp() {
        resumer = new FtkImportResumer(importLogRepository, importService);
    }

    private ImportLog inProgress(int resumeAttempts) {
        return ImportLog.builder()
                .exchangeType(FtkImportService.EXCHANGE_TYPE_FTK)
                .status(ImportStatus.IN_PROGRESS)
                .resumeAttempts(resumeAttempts)
                .build();
    }

    @Test
    @DisplayName("нет незавершённых импортов — ничего не делает")
    void shouldDoNothingWhenNoUnfinishedImports() {
        when(importLogRepository.findByExchangeTypeAndStatus(
                FtkImportService.EXCHANGE_TYPE_FTK, ImportStatus.IN_PROGRESS))
                .thenReturn(List.of());

        resumer.resumeUnfinishedImport();

        verify(importService, never()).resumeImportFromFtp(anyInt());
    }

    @Test
    @DisplayName("незавершённый импорт — помечает INTERRUPTED и запускает возобновление с попыткой +1")
    void shouldMarkInterruptedAndResume() {
        ImportLog row = inProgress(0);
        when(importLogRepository.findByExchangeTypeAndStatus(
                FtkImportService.EXCHANGE_TYPE_FTK, ImportStatus.IN_PROGRESS))
                .thenReturn(List.of(row));

        resumer.resumeUnfinishedImport();

        assertThat(row.getStatus()).isEqualTo(ImportStatus.INTERRUPTED);
        assertThat(row.getErrorMessage()).contains("прерван");
        assertThat(row.getCompletedAt()).isNotNull();
        verify(importLogRepository).saveAll(List.of(row));
        verify(importService).resumeImportFromFtp(1);
    }

    @Test
    @DisplayName("несколько незавершённых записей — попытка считается от максимальной")
    void shouldUseMaxAttemptsAcrossRows() {
        ImportLog first = inProgress(1);
        ImportLog second = inProgress(2);
        when(importLogRepository.findByExchangeTypeAndStatus(
                FtkImportService.EXCHANGE_TYPE_FTK, ImportStatus.IN_PROGRESS))
                .thenReturn(List.of(first, second));

        resumer.resumeUnfinishedImport();

        assertThat(first.getStatus()).isEqualTo(ImportStatus.INTERRUPTED);
        assertThat(second.getStatus()).isEqualTo(ImportStatus.INTERRUPTED);
        verify(importService).resumeImportFromFtp(3);
    }

    @Test
    @DisplayName("лимит попыток исчерпан — помечает INTERRUPTED, но не возобновляет")
    void shouldStopResumingAfterMaxAttempts() {
        ImportLog row = inProgress(FtkImportResumer.MAX_RESUME_ATTEMPTS);
        when(importLogRepository.findByExchangeTypeAndStatus(
                FtkImportService.EXCHANGE_TYPE_FTK, ImportStatus.IN_PROGRESS))
                .thenReturn(List.of(row));

        resumer.resumeUnfinishedImport();

        assertThat(row.getStatus()).isEqualTo(ImportStatus.INTERRUPTED);
        verify(importService, never()).resumeImportFromFtp(anyInt());
    }
}
