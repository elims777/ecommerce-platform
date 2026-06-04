package ru.rfsnab.userservice.controllers;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.rfsnab.userservice.mappers.LegalEntityMapper;
import ru.rfsnab.userservice.models.dto.legal.*;
import ru.rfsnab.userservice.models.dto.legal.LegalEntityAuthRequest;
import ru.rfsnab.userservice.models.dto.legal.LegalEntityAuthResponse;
import ru.rfsnab.userservice.services.LegalEntityService;

import java.util.List;
import java.util.Map;

/**
 * Контроллер для управления юридическими лицами: регистрация, подтверждение email,
 * управление адресами и банковскими счетами.
 */
@RestController
@RequestMapping("/api/v1/legal-entities")
@RequiredArgsConstructor
public class LegalEntityController {

    private final LegalEntityService legalEntityService;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    @PostMapping("/register")
    public ResponseEntity<LegalEntityDto> register(@Valid @RequestBody RegisterLegalEntityRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(LegalEntityMapper.toDto(legalEntityService.register(request)));
    }

    @GetMapping(value = "/confirm-email", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> confirmEmail(@RequestParam String token) {
        String redirectUrl;
        try {
            legalEntityService.confirmEmail(token);
            redirectUrl = frontendUrl + "/login?legal_confirmed=true";
        } catch (Exception e) {
            redirectUrl = frontendUrl + "/login?legal_confirmed=error";
        }
        String html = "<html><head><meta http-equiv=\"refresh\" content=\"0;url=" + redirectUrl + "\"></head><body></body></html>";
        return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body(html);
    }

    @GetMapping(value = "/confirm-link", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> confirmLink(@RequestParam String token) {
        String redirectUrl;
        try {
            legalEntityService.confirmLink(token);
            redirectUrl = frontendUrl + "/profile?link_confirmed=true";
        } catch (Exception e) {
            redirectUrl = frontendUrl + "/profile?link_confirmed=error";
        }
        String html = "<html><head><meta http-equiv=\"refresh\" content=\"0;url=" + redirectUrl + "\"></head><body></body></html>";
        return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body(html);
    }

    @GetMapping("/{id}")
    public ResponseEntity<LegalEntityDto> getById(@PathVariable Long id) {
        return ResponseEntity.ok(LegalEntityMapper.toDto(legalEntityService.getById(id)));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<LegalEntityDto> update(
            @PathVariable Long id,
            @Valid @RequestBody UpdateLegalEntityRequest request) {
        return ResponseEntity.ok(LegalEntityMapper.toDto(legalEntityService.update(id, request)));
    }

    @GetMapping("/{id}/addresses")
    public ResponseEntity<List<LegalEntityAddressDto>> getAddresses(@PathVariable Long id) {
        return ResponseEntity.ok(
                legalEntityService.getAddresses(id).stream()
                        .map(LegalEntityMapper::toAddressDto).toList());
    }

    @PostMapping("/{id}/addresses")
    public ResponseEntity<LegalEntityAddressDto> addAddress(
            @PathVariable Long id,
            @Valid @RequestBody SaveLegalEntityAddressRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(LegalEntityMapper.toAddressDto(legalEntityService.addAddress(id, request)));
    }

    @DeleteMapping("/{id}/addresses/{addressId}")
    public ResponseEntity<Void> deleteAddress(@PathVariable Long id, @PathVariable Long addressId) {
        legalEntityService.deleteAddress(id, addressId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/bank-accounts")
    public ResponseEntity<List<BankAccountDto>> getBankAccounts(@PathVariable Long id) {
        return ResponseEntity.ok(
                legalEntityService.getBankAccounts(id).stream()
                        .map(LegalEntityMapper::toBankAccountDto).toList());
    }

    @PostMapping("/{id}/bank-accounts")
    public ResponseEntity<BankAccountDto> addBankAccount(
            @PathVariable Long id,
            @Valid @RequestBody SaveBankAccountRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(LegalEntityMapper.toBankAccountDto(legalEntityService.addBankAccount(id, request)));
    }

    @DeleteMapping("/{id}/bank-accounts/{accountId}")
    public ResponseEntity<Void> deleteBankAccount(@PathVariable Long id, @PathVariable Long accountId) {
        legalEntityService.deleteBankAccount(id, accountId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/authenticate")
    public ResponseEntity<LegalEntityAuthResponse> authenticate(
            @RequestBody LegalEntityAuthRequest request) {
        return ResponseEntity.ok(legalEntityService.authenticate(request.login(), request.password()));
    }

    @GetMapping("/link-status/{userId}")
    public ResponseEntity<Map<String, Object>> getLinkStatusByUser(@PathVariable Long userId) {
        List<ru.rfsnab.userservice.models.UserLegalEntity> links =
                legalEntityService.getConfirmedLinksForUser(userId);

        if (links.isEmpty()) {
            // нет подтверждённых — проверяем все (включая PENDING)
            List<ru.rfsnab.userservice.models.UserLegalEntity> allLinks =
                    legalEntityService.getAllLinksForUser(userId);
            if (allLinks.isEmpty()) {
                return ResponseEntity.ok(Map.of("linked", false, "confirmed", false));
            }
            ru.rfsnab.userservice.models.UserLegalEntity link = allLinks.get(0);
            return ResponseEntity.ok(Map.of(
                    "linked", true,
                    "confirmed", false,
                    "legalEntityId", link.getLegalEntity().getId()
            ));
        }

        ru.rfsnab.userservice.models.UserLegalEntity link = links.get(0);
        return ResponseEntity.ok(Map.of(
                "linked", true,
                "confirmed", true,
                "legalEntityId", link.getLegalEntity().getId()
        ));
    }

    @GetMapping("/link-status/{userId}/{legalEntityId}")
    public ResponseEntity<Map<String, Boolean>> getLinkStatus(
            @PathVariable Long userId,
            @PathVariable Long legalEntityId) {
        boolean confirmed = legalEntityService.isLinkConfirmed(userId, legalEntityId);
        return ResponseEntity.ok(Map.of("confirmed", confirmed));
    }
}
