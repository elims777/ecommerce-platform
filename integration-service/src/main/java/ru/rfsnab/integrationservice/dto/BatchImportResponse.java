package ru.rfsnab.integrationservice.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * Response от POST /api/v1/products/import/batch.
 * Зеркало BatchProductImportResponse из product-service.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class BatchImportResponse {

    private int totalReceived;
    private int created;
    private int updated;
    /** Товары без изменений значимых полей — не считаются как updated */
    private int unchanged;
    private int failed;
    private List<String> errors = new ArrayList<>();
    /** Постатейный результат импорта (см. product-service BatchProductImportResponse.ImportItemResult) */
    private List<ImportItemResult> results = new ArrayList<>();

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ImportItemResult {
        private String externalId;
        private boolean success;
        /** Сообщение об ошибке (null если success=true) */
        private String errorMessage;
        /** true — ошибка вызвана abort транзакции соседним товаром в чанке, а не самим этим товаром */
        private boolean cascade;
    }
}