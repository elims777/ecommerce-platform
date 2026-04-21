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
import ru.rfsnab.orderservice.mapper.RecipientMapper;
import ru.rfsnab.orderservice.models.dto.recipient.RecipientRequest;
import ru.rfsnab.orderservice.models.dto.recipient.RecipientResponse;
import ru.rfsnab.orderservice.models.entity.Recipient;
import ru.rfsnab.orderservice.service.RecipientService;

import java.util.List;

/**
 * REST контроллер для управления получателями.
 */
@RestController
@RequestMapping("/api/v1/recipients")
@RequiredArgsConstructor
@Tag(name = "Recipients", description = "Управление получателями")
@SecurityRequirement(name = "Bearer Authentication")
public class RecipientController {

    private final RecipientService recipientService;
    private final RecipientMapper recipientMapper;

    @GetMapping
    @Operation(summary = "Список получателей пользователя")
    public ResponseEntity<List<RecipientResponse>> getAll(Authentication authentication) {
        List<Recipient> recipients = recipientService.getByUserId(getCurrentUserId(authentication));
        return ResponseEntity.ok(recipients.stream().map(recipientMapper::toResponse).toList());
    }

    @PostMapping
    @Operation(summary = "Создать получателя")
    public ResponseEntity<RecipientResponse> create(
            Authentication authentication,
            @Valid @RequestBody RecipientRequest request) {
        Recipient recipient = recipientService.create(getCurrentUserId(authentication), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(recipientMapper.toResponse(recipient));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Обновить получателя")
    public ResponseEntity<RecipientResponse> update(
            Authentication authentication,
            @PathVariable Long id,
            @Valid @RequestBody RecipientRequest request) {
        Recipient recipient = recipientService.update(id, getCurrentUserId(authentication), request);
        return ResponseEntity.ok(recipientMapper.toResponse(recipient));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Удалить получателя")
    public ResponseEntity<Void> delete(
            Authentication authentication,
            @PathVariable Long id) {
        recipientService.delete(id, getCurrentUserId(authentication));
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/default")
    @Operation(summary = "Установить получателя по умолчанию")
    public ResponseEntity<RecipientResponse> setDefault(
            Authentication authentication,
            @PathVariable Long id) {
        Recipient recipient = recipientService.setDefault(id, getCurrentUserId(authentication));
        return ResponseEntity.ok(recipientMapper.toResponse(recipient));
    }

    private Long getCurrentUserId(Authentication authentication) {
        return Long.parseLong(authentication.getName());
    }
}