package ru.rfsnab.orderservice.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import ru.rfsnab.orderservice.mapper.WarehousePointMapper;
import ru.rfsnab.orderservice.models.dto.order.WarehousePointDto;
import ru.rfsnab.orderservice.models.dto.order.WarehousePointRequest;
import ru.rfsnab.orderservice.service.WarehousePointService;

import java.util.List;

/**
 * Управление точками самовывоза (админка «Логистика»).
 * Удаления нет — на точки ссылаются заказы, точка выключается флагом active.
 */
@RestController
@RequestMapping("/api/v1/admin/warehouse-points")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
@Tag(name = "Admin Warehouse Points", description = "Точки самовывоза — управление")
@SecurityRequirement(name = "Bearer Authentication")
public class AdminWarehousePointController {

    private final WarehousePointService warehousePointService;

    @GetMapping
    @Operation(summary = "Все точки самовывоза")
    public ResponseEntity<List<WarehousePointDto>> getAll() {
        return ResponseEntity.ok(warehousePointService.getAllPoints().stream()
                .map(WarehousePointMapper::mapToWarehousePointDto)
                .toList());
    }

    @PostMapping
    @Operation(summary = "Создать точку самовывоза")
    public ResponseEntity<WarehousePointDto> create(@Valid @RequestBody WarehousePointRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(WarehousePointMapper.mapToWarehousePointDto(warehousePointService.create(request)));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Изменить точку самовывоза")
    public ResponseEntity<WarehousePointDto> update(@PathVariable Long id,
                                                    @Valid @RequestBody WarehousePointRequest request) {
        return ResponseEntity.ok(WarehousePointMapper.mapToWarehousePointDto(
                warehousePointService.update(id, request)));
    }
}
