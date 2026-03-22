package ru.rfsnab.productservice.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.rfsnab.productservice.dto.BatchProductImportRequest;
import ru.rfsnab.productservice.dto.BatchProductImportResponse;
import ru.rfsnab.productservice.service.ProductImportService;

/**
 * Контроллер для импорта товаров из 1С.
 * Вызывается integration-service, не напрямую из 1С.
 * Endpoint защищён через gateway (JWT), доступ только для service-to-service.
 */
@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
public class ProductImportController {

    private final ProductImportService productImportService;

    @PostMapping("/import/batch")
    public ResponseEntity<BatchProductImportResponse> batchImport(
            @Valid @RequestBody BatchProductImportRequest request){
        BatchProductImportResponse response = productImportService.importBatch(request);
        return ResponseEntity.ok(response);
    }
}
