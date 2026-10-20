package ru.rfsnab.orderservice.delivery.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import ru.rfsnab.orderservice.delivery.models.dto.CarrierDto;
import ru.rfsnab.orderservice.delivery.models.dto.CarrierSyncReport;
import ru.rfsnab.orderservice.delivery.models.dto.CarrierUpdateRequest;
import ru.rfsnab.orderservice.delivery.models.entity.enums.CarrierCode;
import ru.rfsnab.orderservice.delivery.service.CarrierService;
import ru.rfsnab.orderservice.delivery.service.CarrierSyncService;

import java.util.List;

/**
 * Управление транспортными компаниями (админка «Логистика»).
 */
@RestController
@RequestMapping("/api/v1/admin/carriers")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
@Tag(name = "Admin Carriers", description = "Транспортные компании — настройки")
@SecurityRequirement(name = "Bearer Authentication")
public class AdminCarrierController {

    private final CarrierService carrierService;
    private final CarrierSyncService carrierSyncService;

    @GetMapping
    @Operation(summary = "Все транспортные компании")
    public ResponseEntity<List<CarrierDto>> getAll() {
        return ResponseEntity.ok(carrierService.getAll());
    }

    @PatchMapping("/{code}")
    @Operation(summary = "Обновить настройки ТК")
    public ResponseEntity<CarrierDto> update(@PathVariable CarrierCode code,
                                             @Valid @RequestBody CarrierUpdateRequest request) {
        return ResponseEntity.ok(carrierService.update(code, request));
    }

    /** Ручная синхронизация терминалов ТК; возвращает отчёт. */
    @PostMapping("/{code}/sync-terminals")
    @Operation(summary = "Синхронизировать терминалы ТК сейчас; ошибка ТК приходит отчётом со статусом ERROR")
    public ResponseEntity<CarrierSyncReport> syncTerminals(@PathVariable CarrierCode code) {
        return ResponseEntity.ok(carrierSyncService.syncManually(code));
    }
}
