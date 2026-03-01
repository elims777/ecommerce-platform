package ru.rfsnab.orderservice.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.rfsnab.orderservice.mapper.WarehousePointMapper;
import ru.rfsnab.orderservice.models.dto.order.WarehousePointDto;
import ru.rfsnab.orderservice.service.WarehousePointService;

import java.util.List;

/**
 * REST контроллер для точек самовывоза.
 * Используется покупателем при выборе способа доставки PICKUP.
 */
@RestController
@RequestMapping("/api/v1/warehouse-points")
@RequiredArgsConstructor
@Tag(name = "Warehouse Points", description = "Точки самовывоза")
@SecurityRequirement(name = "Bearer Authentication")
public class WarehousePointController {

    private final WarehousePointService warehousePointService;

    @GetMapping
    @Operation(summary = "Список активных точек самовывоза")
    public ResponseEntity<List<WarehousePointDto>> getActivePoints() {
        List<WarehousePointDto> points = warehousePointService.getActivePoints().stream()
                .map(WarehousePointMapper::mapToWarehousePointDto)
                .toList();
        return ResponseEntity.ok(points);
    }
}