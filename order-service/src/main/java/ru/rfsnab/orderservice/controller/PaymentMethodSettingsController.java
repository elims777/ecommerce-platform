package ru.rfsnab.orderservice.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import ru.rfsnab.orderservice.models.dto.payment.PaymentMethodSettingsDto;
import ru.rfsnab.orderservice.service.PaymentMethodSettingsService;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "PaymentSettings", description = "Настройки методов оплаты")
public class PaymentMethodSettingsController {

    private final PaymentMethodSettingsService service;

    @GetMapping("/payment-settings")
    @Operation(summary = "Получить настройки методов оплаты (публичный)")
    public ResponseEntity<PaymentMethodSettingsDto> getSettings() {
        return ResponseEntity.ok(service.getSettings());
    }

    @PutMapping("/admin/payment-settings")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    @Operation(summary = "Обновить настройки методов оплаты (только ADMIN)")
    public ResponseEntity<PaymentMethodSettingsDto> updateSettings(
            @RequestBody PaymentMethodSettingsDto dto) {
        return ResponseEntity.ok(service.updateSettings(dto));
    }
}
