package ru.rfsnab.notificationservice.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.rfsnab.notificationservice.models.PasswordResetToken;
import ru.rfsnab.notificationservice.service.EmailService;
import ru.rfsnab.notificationservice.service.PasswordResetTokenService;

import java.util.Map;
import java.util.Optional;

@Slf4j
@RestController
@RequestMapping("/api/password-reset")
@RequiredArgsConstructor
public class PasswordResetController {

    private final PasswordResetTokenService tokenService;
    private final EmailService emailService;

    @PostMapping("/create")
    public ResponseEntity<Void> create(@RequestBody CreateResetRequest request) {
        log.info("Создание токена сброса пароля для accountId: {}, accountType: {}",
                request.accountId(), request.accountType());

        tokenService.create(request.accountId(), request.accountType(), request.email(), request.rawToken());
        emailService.sendPasswordResetEmail(request.email(), request.firstName(), request.rawToken());

        return ResponseEntity.ok().build();
    }

    @PostMapping("/consume")
    public ResponseEntity<ConsumeResponse> consume(@RequestParam String token) {
        Optional<PasswordResetToken> consumed = tokenService.consume(token);

        return consumed
                .map(t -> ResponseEntity.ok(new ConsumeResponse(true, t.getAccountId(), t.getAccountType())))
                .orElseGet(() -> ResponseEntity.ok(new ConsumeResponse(false, null, null)));
    }

    @GetMapping("/validate")
    public ResponseEntity<Map<String, Boolean>> validate(@RequestParam String token) {
        return ResponseEntity.ok(Map.of("valid", tokenService.isValid(token)));
    }

    public record CreateResetRequest(Long accountId, String accountType, String email,
                                      String firstName, String rawToken) {
    }

    public record ConsumeResponse(boolean valid, Long accountId, String accountType) {
    }
}
