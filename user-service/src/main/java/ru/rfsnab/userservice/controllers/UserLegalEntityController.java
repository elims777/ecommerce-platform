package ru.rfsnab.userservice.controllers;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import ru.rfsnab.userservice.mappers.LegalEntityMapper;
import ru.rfsnab.userservice.models.dto.legal.LegalEntityDto;
import ru.rfsnab.userservice.services.LegalEntityService;

import java.util.List;
import java.util.Map;

/**
 * Контроллер для привязки физических пользователей к юридическим лицам из личного кабинета.
 */
@RestController
@RequestMapping("/api/v1/users/me/legal-entities")
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

    @GetMapping
    public ResponseEntity<List<LegalEntityDto>> getMyLegalEntities(Authentication authentication) {
        Long userId = Long.parseLong(authentication.getName());
        return ResponseEntity.ok(
                legalEntityService.getConfirmedLinksForUser(userId).stream()
                        .map(link -> LegalEntityMapper.toDto(link.getLegalEntity()))
                        .toList());
    }
}
