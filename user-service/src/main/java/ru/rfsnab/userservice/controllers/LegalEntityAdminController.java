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
    public ResponseEntity<List<LegalEntityDto>> getByStatus(
            @RequestParam(defaultValue = "PENDING") VerificationStatus status) {
        return ResponseEntity.ok(
                legalEntityService.getByVerificationStatus(status).stream()
                        .map(LegalEntityMapper::toDto).toList());
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
}
