package ru.rfsnab.productservice.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.rfsnab.productservice.dto.ProductStockResponse;
import ru.rfsnab.productservice.dto.WarehouseRequest;
import ru.rfsnab.productservice.dto.WarehouseResponse;
import ru.rfsnab.productservice.mapper.WarehouseMapper;
import ru.rfsnab.productservice.service.ProductStockService;
import ru.rfsnab.productservice.service.WarehouseService;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
public class WarehouseController {

    private final WarehouseService warehouseService;
    private final ProductStockService stockService;

    /** Публично: активные склады для карточки и оформления. */
    @GetMapping("/warehouses")
    public List<WarehouseResponse> getActive() {
        return warehouseService.getActive().stream().map(WarehouseMapper::toResponse).toList();
    }

    /** Публично: остатки по складам для набора товаров (корзина/оформление). */
    @GetMapping("/stocks")
    public Map<Long, List<ProductStockResponse>> getStocks(@RequestParam List<Long> ids) {
        return stockService.getStocks(ids);
    }

    @GetMapping("/admin/warehouses")
    public List<WarehouseResponse> getAll() {
        return warehouseService.getAll().stream().map(WarehouseMapper::toResponse).toList();
    }

    @PostMapping("/admin/warehouses")
    public ResponseEntity<WarehouseResponse> create(@Valid @RequestBody WarehouseRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(WarehouseMapper.toResponse(warehouseService.create(request)));
    }

    @PutMapping("/admin/warehouses/{id}")
    public WarehouseResponse update(@PathVariable Long id, @Valid @RequestBody WarehouseRequest request) {
        return WarehouseMapper.toResponse(warehouseService.update(id, request));
    }
}
