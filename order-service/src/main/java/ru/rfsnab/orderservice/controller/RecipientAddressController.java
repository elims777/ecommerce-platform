package ru.rfsnab.orderservice.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import ru.rfsnab.orderservice.mapper.RecipientAddressMapper;
import ru.rfsnab.orderservice.models.dto.recipient.RecipientAddressRequest;
import ru.rfsnab.orderservice.models.dto.recipient.RecipientAddressResponse;
import ru.rfsnab.orderservice.models.entity.RecipientAddress;
import ru.rfsnab.orderservice.service.RecipientAddressService;

import java.util.List;

/**
 * REST контроллер для управления адресами получателей.
 */
@RestController
@RequestMapping("/api/v1/recipients/{recipientId}/addresses")
@RequiredArgsConstructor
@Tag(name = "Recipient Addresses", description = "Управление адресами получателей")
@SecurityRequirement(name = "Bearer Authentication")
public class RecipientAddressController {

    private final RecipientAddressService addressService;
    private final RecipientAddressMapper addressMapper;

    @GetMapping
    @Operation(summary = "Список адресов получателя")
    public ResponseEntity<List<RecipientAddressResponse>> getAll(
            Authentication authentication,
            @PathVariable Long recipientId) {
        List<RecipientAddress> addresses = addressService.getByRecipientId(recipientId, getCurrentUserId(authentication));
        return ResponseEntity.ok(addresses.stream().map(addressMapper::toResponse).toList());
    }

    @PostMapping
    @Operation(summary = "Создать адрес получателя")
    public ResponseEntity<RecipientAddressResponse> create(
            Authentication authentication,
            @PathVariable Long recipientId,
            @Valid @RequestBody RecipientAddressRequest request) {
        RecipientAddress address = addressService.create(recipientId, getCurrentUserId(authentication), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(addressMapper.toResponse(address));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Обновить адрес получателя")
    public ResponseEntity<RecipientAddressResponse> update(
            Authentication authentication,
            @PathVariable Long recipientId,
            @PathVariable Long id,
            @Valid @RequestBody RecipientAddressRequest request) {
        RecipientAddress address = addressService.update(id, recipientId, getCurrentUserId(authentication), request);
        return ResponseEntity.ok(addressMapper.toResponse(address));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Удалить адрес получателя")
    public ResponseEntity<Void> delete(
            Authentication authentication,
            @PathVariable Long recipientId,
            @PathVariable Long id) {
        addressService.delete(id, recipientId, getCurrentUserId(authentication));
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/default")
    @Operation(summary = "Установить адрес по умолчанию")
    public ResponseEntity<RecipientAddressResponse> setDefault(
            Authentication authentication,
            @PathVariable Long recipientId,
            @PathVariable Long id) {
        RecipientAddress address = addressService.setDefault(id, recipientId, getCurrentUserId(authentication));
        return ResponseEntity.ok(addressMapper.toResponse(address));
    }

    private Long getCurrentUserId(Authentication authentication) {
        return Long.parseLong(authentication.getName());
    }
}