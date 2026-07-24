package ru.rfsnab.notificationservice.repository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;
import ru.rfsnab.notificationservice.models.PasswordResetToken;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class PasswordResetTokenRepositoryTest {

    @Autowired
    private PasswordResetTokenRepository tokenRepository;

    @Test
    void save_and_findByTokenHash_returnsSavedEntity() {
        LocalDateTime now = LocalDateTime.now();
        PasswordResetToken token = PasswordResetToken.builder()
                .tokenHash("hash-abc-123")
                .accountId(1L)
                .accountType("USER")
                .email("test@example.com")
                .createdAt(now)
                .expiresAt(now.plusMinutes(30))
                .build();

        tokenRepository.save(token);

        Optional<PasswordResetToken> found = tokenRepository.findByTokenHash("hash-abc-123");

        assertThat(found).isPresent();
        assertThat(found.get().getAccountId()).isEqualTo(1L);
        assertThat(found.get().getAccountType()).isEqualTo("USER");
        assertThat(found.get().getEmail()).isEqualTo("test@example.com");
        assertThat(found.get().isUsed()).isFalse();
    }

    @Test
    void findByTokenHash_nonExisting_returnsEmpty() {
        Optional<PasswordResetToken> found = tokenRepository.findByTokenHash("non-existing-hash");

        assertThat(found).isEmpty();
    }

    @Test
    void deleteAllByAccountIdAndAccountType_removesOnlyMatchingAccountRecords() {
        LocalDateTime now = LocalDateTime.now();
        tokenRepository.save(PasswordResetToken.builder()
                .tokenHash("hash-user-1")
                .accountId(10L)
                .accountType("USER")
                .email("user@example.com")
                .createdAt(now)
                .expiresAt(now.plusMinutes(30))
                .build());
        tokenRepository.save(PasswordResetToken.builder()
                .tokenHash("hash-user-2")
                .accountId(10L)
                .accountType("USER")
                .email("user@example.com")
                .createdAt(now)
                .expiresAt(now.plusMinutes(30))
                .build());
        tokenRepository.save(PasswordResetToken.builder()
                .tokenHash("hash-legal-entity-1")
                .accountId(10L)
                .accountType("LEGAL_ENTITY")
                .email("legal@example.com")
                .createdAt(now)
                .expiresAt(now.plusMinutes(30))
                .build());
        tokenRepository.save(PasswordResetToken.builder()
                .tokenHash("hash-other-account")
                .accountId(20L)
                .accountType("USER")
                .email("other@example.com")
                .createdAt(now)
                .expiresAt(now.plusMinutes(30))
                .build());

        tokenRepository.deleteAllByAccountIdAndAccountType(10L, "USER");

        assertThat(tokenRepository.findByTokenHash("hash-user-1")).isEmpty();
        assertThat(tokenRepository.findByTokenHash("hash-user-2")).isEmpty();
        assertThat(tokenRepository.findByTokenHash("hash-legal-entity-1")).isPresent();
        assertThat(tokenRepository.findByTokenHash("hash-other-account")).isPresent();
    }
}
