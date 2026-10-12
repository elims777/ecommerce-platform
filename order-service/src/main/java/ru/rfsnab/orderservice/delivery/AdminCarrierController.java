package ru.rfsnab.orderservice.delivery;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

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

    @GetMapping
    @Operation(summary = "Все транспортные компании")
    public ResponseEntity<List<CarrierDto>> getAll() {
        return ResponseEntity.ok(carrierService.getAll().stream().map(CarrierDto::from).toList());
    }

    @PatchMapping("/{code}")
    @Operation(summary = "Обновить настройки ТК")
    public ResponseEntity<CarrierDto> update(@PathVariable CarrierCode code,
                                             @Valid @RequestBody CarrierUpdateRequest request) {
        return ResponseEntity.ok(CarrierDto.from(carrierService.update(code, request)));
    }
}
