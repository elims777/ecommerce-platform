package ru.rfsnab.userservice.controllers;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
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

    @PostMapping("/register")
    public ResponseEntity<LegalEntityDto> register(@Valid @RequestBody RegisterLegalEntityRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(LegalEntityMapper.toDto(legalEntityService.register(request)));
    }

    @GetMapping("/confirm-email")
    public ResponseEntity<Void> confirmEmail(@RequestParam String token) {
        legalEntityService.confirmEmail(token);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/confirm-link")
    public ResponseEntity<Void> confirmLink(@RequestParam String token) {
        legalEntityService.confirmLink(token);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{id}")
    public ResponseEntity<LegalEntityDto> getById(@PathVariable Long id) {
        return ResponseEntity.ok(LegalEntityMapper.toDto(legalEntityService.getById(id)));
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

    @GetMapping("/link-status/{userId}/{legalEntityId}")
    public ResponseEntity<Map<String, Boolean>> getLinkStatus(
            @PathVariable Long userId,
            @PathVariable Long legalEntityId) {
        boolean confirmed = legalEntityService.isLinkConfirmed(userId, legalEntityId);
        return ResponseEntity.ok(Map.of("confirmed", confirmed));
    }
}
