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
}