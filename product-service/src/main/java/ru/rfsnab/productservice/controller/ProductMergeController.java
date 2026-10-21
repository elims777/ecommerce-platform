package ru.rfsnab.productservice.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.rfsnab.productservice.dto.ProductResponse;
import ru.rfsnab.productservice.dto.ProductStockResponse;
import ru.rfsnab.productservice.mapper.ProductMapper;
import ru.rfsnab.productservice.model.Product;
import ru.rfsnab.productservice.service.ProductMergeService;
import ru.rfsnab.productservice.service.ProductStockService;

import java.util.List;
import java.util.Map;

/** Админка: ручная склейка товаров 1С с товарами Факела и просмотр собственных остатков записи. */
@RestController
@RequestMapping("/api/v1/products/admin")
@RequiredArgsConstructor
public class ProductMergeController {

    private final ProductMergeService mergeService;
    private final ProductStockService stockService;

    /** Склеенные с товаром записи 1С; stocks — их собственные остатки. */
    @GetMapping("/{id}/merged")
    public List<ProductResponse> getMerged(@PathVariable Long id) {
        List<Product> merged = mergeService.getMerged(id);
        Map<Long, List<ProductStockResponse>> stocks =
                stockService.getOwnStocks(merged.stream().map(Product::getId).toList());
        return merged.stream().map(p -> {
            ProductResponse response = ProductMapper.mapToResponse(p);
            response.setStocks(stocks.getOrDefault(p.getId(), List.of()));
            return response;
        }).toList();
    }

    /** Собственные остатки записи (без склеенных), включая нулевые. */
    @GetMapping("/{id}/stocks")
    public List<ProductStockResponse> getOwnStocks(@PathVariable Long id) {
        return stockService.getOwnStocks(List.of(id)).getOrDefault(id, List.of());
    }

    @GetMapping("/merge-candidates")
    public Page<ProductResponse> getMergeCandidates(
            @RequestParam(defaultValue = "") String query,
            @PageableDefault(size = 20) Pageable pageable) {
        return mergeService.findCandidates(query, pageable).map(ProductMapper::mapToResponse);
    }

    @PostMapping("/{id}/merged/{oneCId}")
    public ResponseEntity<Void> merge(@PathVariable Long id, @PathVariable Long oneCId) {
        mergeService.merge(id, oneCId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/merged/{oneCId}")
    public ResponseEntity<Void> unmerge(@PathVariable Long oneCId) {
        mergeService.unmerge(oneCId);
        return ResponseEntity.noContent().build();
    }
}
