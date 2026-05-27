package ru.rfsnab.productservice.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.util.List;

/**
 * Запрос на batch-импорт товаров из 1С.
 * Принимает пачку до 100 товаров за один вызов.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BatchProductImportRequest {

    @NotEmpty(message = "Список товаров не может быть пустым")
    @Size(max = 100, message = "Максимум 100 товаров за 1 запрос")
    @Valid
    private List<ProductImportItem> items;
}
