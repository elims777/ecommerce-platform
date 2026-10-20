package ru.rfsnab.orderservice.delivery.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import ru.rfsnab.orderservice.delivery.models.dto.DocumentFile;
import ru.rfsnab.orderservice.delivery.models.dto.LinkShipmentRequest;
import ru.rfsnab.orderservice.delivery.models.dto.ShipmentDto;
import ru.rfsnab.orderservice.delivery.models.dto.ShipmentImportStatus;
import ru.rfsnab.orderservice.delivery.models.entity.enums.CarrierCode;
import ru.rfsnab.orderservice.delivery.models.entity.enums.ShipmentDirection;
import ru.rfsnab.orderservice.delivery.models.entity.enums.ShipmentStatusCode;
import ru.rfsnab.orderservice.delivery.models.dto.ShipmentFilter;
import ru.rfsnab.orderservice.delivery.service.ShipmentJournalImporter;
import ru.rfsnab.orderservice.delivery.service.ShipmentService;

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
    private final ShipmentJournalImporter journalImporter;

    /** Журнал с фильтрами и пагинацией; from/to — ISO-моменты, to исключительно. */
    @GetMapping
    @Operation(summary = "Журнал отправлений с фильтрами")
    public ResponseEntity<Page<ShipmentDto>> getJournal(
            @RequestParam(required = false) CarrierCode carrier,
            @RequestParam(required = false) ShipmentDirection direction,
            @RequestParam(required = false) ShipmentStatusCode statusCode,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String counterparty,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(defaultValue = "false") boolean problem,
            @RequestParam(defaultValue = "false") boolean notFound,
            @RequestParam(defaultValue = "false") boolean stale,
            @RequestParam(defaultValue = "false") boolean awaitingMore,
            @PageableDefault(size = 20) Pageable pageable) {
        ShipmentFilter filter = new ShipmentFilter(carrier, direction, statusCode, city, counterparty, search, from, to,
                problem, notFound, stale, awaitingMore);
        return ResponseEntity.ok(shipmentService.getJournal(filter, pageable));
    }

    /** Запустить импорт журналов ТК сейчас: 202, если запущен; 409, если импорт уже идёт. */
    @PostMapping("/import")
    @Operation(summary = "Импортировать журналы ТК сейчас")
    public ResponseEntity<Void> importJournals() {
        return journalImporter.importAsync()
                ? ResponseEntity.accepted().build()
                : ResponseEntity.status(HttpStatus.CONFLICT).build();
    }

    /** Идёт ли импорт журналов сейчас (фронт опрашивает после запуска). */
    @GetMapping("/import")
    @Operation(summary = "Статус импорта журналов ТК")
    public ResponseEntity<ShipmentImportStatus> importStatus() {
        return ResponseEntity.ok(new ShipmentImportStatus(journalImporter.isRunning()));
    }

    /** Опросить ТК по отправлению сейчас. */
    @PostMapping("/{id}/refresh")
    @Operation(summary = "Обновить статус отправления")
    public ResponseEntity<ShipmentDto> refresh(@PathVariable Long id) {
        return ResponseEntity.ok(shipmentService.refresh(id));
    }

    /** Скачать документ отправления из ТК; 404 — нет отправления или документа с таким кодом, 502 — ошибка ТК. */
    @GetMapping("/{id}/documents/{code}")
    @Operation(summary = "Скачать документ отправления")
    public ResponseEntity<byte[]> downloadDocument(@PathVariable Long id, @PathVariable String code) {
        DocumentFile file = shipmentService.downloadDocument(id, code);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(file.fileName()).build().toString())
                .body(file.content());
    }

    /** Привязать отправление к заказу сайта по номеру заказа. */
    @PostMapping("/{id}/link")
    @Operation(summary = "Привязать отправление к заказу")
    public ResponseEntity<ShipmentDto> link(@PathVariable Long id, @Valid @RequestBody LinkShipmentRequest request) {
        return ResponseEntity.ok(shipmentService.link(id, request.orderNumber()));
    }
}
