package ru.rfsnab.integrationservice.dto;

import java.util.List;

/**
 * Агрегированный результат импорта каталога — собирается из ответов всех chunk'ов.
 */
public record ImportResult(
        int totalItems,
        int createdCount,
        int updatedCount,
        int failedCount,
        List<String> errors
) {

    public boolean isFullySuccessful() {
        return failedCount == 0 && errors.isEmpty();
    }

    public boolean isPartiallySuccessful() {
        return failedCount > 0 && (createdCount > 0 || updatedCount > 0);
    }
}