package ru.rfsnab.notificationservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.rfsnab.notificationservice.models.PasswordResetToken;
import ru.rfsnab.notificationservice.repository.PasswordResetTokenRepository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class PasswordResetTokenService {

    private static final Duration TTL = Duration.ofHours(1);

    private final PasswordResetTokenRepository tokenRepository;

    public void create(Long accountId, String accountType, String email, String rawToken) {
        tokenRepository.deleteAllByAccountIdAndAccountType(accountId, accountType);

        LocalDateTime now = LocalDateTime.now();
        PasswordResetToken token = PasswordResetToken.builder()
                .tokenHash(sha256Hex(rawToken))
                .accountId(accountId)
                .accountType(accountType)
                .email(email)
                .createdAt(now)
                .expiresAt(now.plus(TTL))
                .used(false)
                .build();

        tokenRepository.save(token);
        log.info("Создан токен сброса пароля для accountId: {}, accountType: {}", accountId, accountType);
    }

    public Optional<PasswordResetToken> consume(String rawToken) {
        Optional<PasswordResetToken> found = tokenRepository.findByTokenHash(sha256Hex(rawToken));
        if (found.isEmpty()) {
            return Optional.empty();
        }

        PasswordResetToken token = found.get();
        if (token.isUsed() || isExpired(token)) {
            return Optional.empty();
        }

        token.setUsed(true);
        tokenRepository.save(token);
        log.info("Токен сброса пароля использован для accountId: {}", token.getAccountId());

        return Optional.of(token);
    }

    public boolean isValid(String rawToken) {
        return tokenRepository.findByTokenHash(sha256Hex(rawToken))
                .filter(token -> !token.isUsed() && !isExpired(token))
                .isPresent();
    }

    private static boolean isExpired(PasswordResetToken token) {
        return LocalDateTime.now().isAfter(token.getExpiresAt());
    }

    private static String sha256Hex(String input) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 недоступен в JVM", e);
        }
    }
}
