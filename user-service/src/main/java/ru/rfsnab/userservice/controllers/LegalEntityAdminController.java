package ru.rfsnab.userservice.controllers;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.rfsnab.userservice.mappers.LegalEntityMapper;
import ru.rfsnab.userservice.models.dto.legal.LegalEntityDto;
import ru.rfsnab.userservice.models.enums.VerificationStatus;
import ru.rfsnab.userservice.services.LegalEntityService;

import java.util.List;
import java.util.Map;

/**
 * Административный контроллер для верификации юридических лиц менеджером.
 */
@RestController
@RequestMapping("/api/v1/admin/legal-entities")
@RequiredArgsConstructor
public class LegalEntityAdminController {

    private final LegalEntityService legalEntityService;

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
}
