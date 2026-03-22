package ru.rfsnab.productservice.dto;

import lombok.*;

import java.util.List;

/**
 * Результат batch-импорта товаров из 1С.
 * Возвращается integration-service для формирования ответа 1С.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BatchProductImportResponse {
    private int totalReceived;
    private int created;
    private int updated;
    private int failed;
    private List<ImportItemResult> results;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ImportItemResult {
        private String externalId;
        private Long productId;
        private ImportAction action;
        private boolean success;
        /** Сообщение об ошибке (null если success=true) */
        private String errorMessage;
    }

    public enum ImportAction {
        CREATED, UPDATED, FAILED
    }
}
