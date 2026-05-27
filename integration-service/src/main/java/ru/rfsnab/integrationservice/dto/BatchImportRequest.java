package ru.rfsnab.integrationservice.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * Request для POST /api/v1/products/import/batch в product-service.
 * Зеркало BatchProductImportRequest.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class BatchImportRequest {

    private List<ProductImportItemDto> items;
}
