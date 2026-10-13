package ru.rfsnab.orderservice.delivery;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

/**
 * Журнал отправлений компании (админка «Логистика»). Отправления конкретного заказа —
 * в {@code AdminOrderController}.
 */
@RestController
@RequestMapping("/api/v1/admin/shipments")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
@Tag(name = "Admin Shipments", description = "Журнал отправлений")
@SecurityRequirement(name = "Bearer Authentication")
public class AdminShipmentController {

    private final ShipmentService shipmentService;

    /** Журнал с фильтрами и пагинацией; from/to — ISO-моменты, to исключительно. */
    @GetMapping
    @Operation(summary = "Журнал отправлений с фильтрами")
    public ResponseEntity<Page<ShipmentDto>> getJournal(
            @RequestParam(required = false) CarrierCode carrier,
            @RequestParam(required = false) ShipmentDirection direction,
            @RequestParam(required = false) ShipmentStatusCode statusCode,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(defaultValue = "false") boolean problem,
            @RequestParam(defaultValue = "false") boolean notFound,
            @RequestParam(defaultValue = "false") boolean stale,
            @RequestParam(defaultValue = "false") boolean awaitingMore,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        ShipmentFilter filter = new ShipmentFilter(carrier, direction, statusCode, city, from, to,
                problem, notFound, stale, awaitingMore);
        return ResponseEntity.ok(shipmentService.getJournal(filter, pageable));
    }

    /** Опросить ТК по отправлению сейчас. */
    @PostMapping("/{id}/refresh")
    @Operation(summary = "Обновить статус отправления")
    public ResponseEntity<ShipmentDto> refresh(@PathVariable Long id) {
        return ResponseEntity.ok(shipmentService.refresh(id));
    }

    /** Привязать отправление к заказу сайта по номеру заказа. */
    @PostMapping("/{id}/link")
    @Operation(summary = "Привязать отправление к заказу")
    public ResponseEntity<ShipmentDto> link(@PathVariable Long id, @Valid @RequestBody LinkShipmentRequest request) {
        return ResponseEntity.ok(shipmentService.link(id, request.orderNumber()));
    }
}
