package ru.rfsnab.userservice.controllers;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import ru.rfsnab.userservice.exceptions.LegalEntityNotFoundException;
import ru.rfsnab.userservice.mappers.LegalEntityMapper;
import ru.rfsnab.userservice.models.UserLegalEntity;
import ru.rfsnab.userservice.models.dto.legal.LegalEntityDto;
import ru.rfsnab.userservice.services.LegalEntityService;

import java.util.List;
import java.util.Map;

/**
 * Контроллер для привязки физических пользователей к юридическим лицам из личного кабинета.
 */
@RestController
@RequestMapping("/v1/users/me/legal-entities")
@RequiredArgsConstructor
public class UserLegalEntityController {

    private final LegalEntityService legalEntityService;

    @PostMapping("/link")
    public ResponseEntity<Void> link(Authentication authentication,
                                     @RequestBody Map<String, String> body) {
        Long userId = Long.parseLong(authentication.getName());
        legalEntityService.linkToUser(userId, body.get("inn"));
        return ResponseEntity.ok().build();
    }

    @PostMapping("/resend-link")
    public ResponseEntity<Void> resendLink(Authentication authentication) {
        Long userId = Long.parseLong(authentication.getName());
        legalEntityService.resendLink(userId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/resend-verification")
    public ResponseEntity<Void> resendVerification(Authentication authentication) {
        Long userId = Long.parseLong(authentication.getName());
        UserLegalEntity link = legalEntityService.getAllLinksForUser(userId).stream()
                .filter(l -> !l.getLegalEntity().isEmailVerified())
                .findFirst()
                .orElseThrow(() -> new LegalEntityNotFoundException(
                        "Нет привязанного юрлица с неподтверждённым email"));
        legalEntityService.resendEmailConfirmation(link.getLegalEntity().getId());
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{legalEntityId}")
    public ResponseEntity<Void> unlink(Authentication authentication,
                                       @PathVariable Long legalEntityId) {
        Long userId = Long.parseLong(authentication.getName());
        legalEntityService.detachFromUser(legalEntityId, userId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    public ResponseEntity<List<LegalEntityDto>> getMyLegalEntities(Authentication authentication) {
        Long userId = Long.parseLong(authentication.getName());
        return ResponseEntity.ok(
                legalEntityService.getConfirmedLinksForUser(userId).stream()
                        .map(link -> LegalEntityMapper.toDto(link.getLegalEntity()))
                        .toList());
    }
}
