package ru.rfsnab.userservice.controllers;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import ru.rfsnab.userservice.exceptions.LegalEntityDeletionNotAllowedException;
import ru.rfsnab.userservice.mappers.LegalEntityMapper;
import ru.rfsnab.userservice.models.LegalEntity;
import ru.rfsnab.userservice.models.dto.legal.LegalEntityDto;
import ru.rfsnab.userservice.models.enums.VerificationStatus;
import ru.rfsnab.userservice.services.LegalEntityService;
import ru.rfsnab.userservice.services.client.OrderServiceClient;

import java.util.List;
import java.util.Map;

/**
 * Административный контроллер для верификации юридических лиц менеджером.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/admin/legal-entities")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
public class LegalEntityAdminController {

    private final LegalEntityService legalEntityService;
    private final OrderServiceClient orderServiceClient;

    @GetMapping
    public ResponseEntity<List<LegalEntityDto>> getAll(
            @RequestParam(required = false) VerificationStatus status) {
        List<LegalEntityDto> result = status != null
                ? legalEntityService.getByVerificationStatus(status).stream().map(LegalEntityMapper::toDto).toList()
                : legalEntityService.getAll().stream().map(LegalEntityMapper::toDto).toList();
        return ResponseEntity.ok(result);
    }

    @GetMapping("/{id}")
    public ResponseEntity<LegalEntityDto> getById(@PathVariable Long id) {
        return ResponseEntity.ok(LegalEntityMapper.toDto(legalEntityService.getById(id)));
    }

    @GetMapping("/users/{userId}")
    public ResponseEntity<List<LegalEntityDto>> getByUser(@PathVariable Long userId) {
        return ResponseEntity.ok(
                legalEntityService.getAllLinksForUser(userId).stream()
                        .map(link -> LegalEntityMapper.toDto(link.getLegalEntity()))
                        .toList());
    }

    @PostMapping("/{id}/verify")
    public ResponseEntity<LegalEntityDto> verify(
            @PathVariable Long id,
            @RequestParam String managerEmail) {
        return ResponseEntity.ok(LegalEntityMapper.toDto(legalEntityService.verify(id, managerEmail)));
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<LegalEntityDto> reject(
            @PathVariable Long id,
            @RequestParam String managerEmail,
            @RequestBody Map<String, String> body) {
        return ResponseEntity.ok(LegalEntityMapper.toDto(
                legalEntityService.reject(id, managerEmail, body.get("reason"))));
    }

    @DeleteMapping("/{id}/users/{userId}")
    public ResponseEntity<Void> detachFromUser(
            @PathVariable Long id,
            @PathVariable Long userId) {
        legalEntityService.detachFromUser(id, userId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @PathVariable Long id,
            @RequestHeader("X-User-Id") String callerUserId,
            @RequestHeader("Authorization") String authorization) {

        LegalEntity entity = legalEntityService.getById(id);

        if (entity.getInn() == null || entity.getInn().isBlank()) {
            throw new LegalEntityDeletionNotAllowedException(
                    "Нельзя удалить юрлицо без ИНН — проверка активных заказов невозможна");
        }

        long activeOrders = orderServiceClient.countActiveOrdersByInn(entity.getInn(), authorization);
        if (activeOrders > 0) {
            throw new LegalEntityDeletionNotAllowedException(
                    "Нельзя удалить юрлицо с активными заказами (" + activeOrders + ")");
        }

        legalEntityService.deleteById(id);
        log.info("Юрлицо {} удалено администратором {}", id, callerUserId);
        return ResponseEntity.noContent().build();
    }
}
