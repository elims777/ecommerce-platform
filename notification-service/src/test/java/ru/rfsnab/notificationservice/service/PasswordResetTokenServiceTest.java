package ru.rfsnab.notificationservice.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.rfsnab.notificationservice.models.PasswordResetToken;
import ru.rfsnab.notificationservice.repository.PasswordResetTokenRepository;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PasswordResetTokenService Unit Tests")
class PasswordResetTokenServiceTest {

    @Mock
    private PasswordResetTokenRepository tokenRepository;

    private PasswordResetTokenService tokenService;

    private static final String RAW_TOKEN = "raw-token-123";

    @BeforeEach
    void setUp() {
        tokenService = new PasswordResetTokenService(tokenRepository);
    }

    private static String sha256Hex(String input) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    // ==================== create() Tests ====================

    @Nested
    @DisplayName("create()")
    class CreateTests {

        @Test
        @DisplayName("удаляет старые токены аккаунта и сохраняет хеш, а не сырой токен")
        void create_ValidInput_DeletesOldAndSavesHash() {
            // Given
            ArgumentCaptor<PasswordResetToken> captor = ArgumentCaptor.forClass(PasswordResetToken.class);
            when(tokenRepository.save(any(PasswordResetToken.class))).thenAnswer(inv -> inv.getArgument(0));

            // When
            tokenService.create(1L, "USER", "test@example.com", RAW_TOKEN);

            // Then
            verify(tokenRepository).deleteAllByAccountIdAndAccountType(1L, "USER");
            verify(tokenRepository).save(captor.capture());

            PasswordResetToken saved = captor.getValue();
            assertThat(saved.getTokenHash()).isEqualTo(sha256Hex(RAW_TOKEN));
            assertThat(saved.getTokenHash()).isNotEqualTo(RAW_TOKEN);
            assertThat(saved.getAccountId()).isEqualTo(1L);
            assertThat(saved.getAccountType()).isEqualTo("USER");
            assertThat(saved.getEmail()).isEqualTo("test@example.com");
            assertThat(saved.isUsed()).isFalse();
            assertThat(saved.getExpiresAt()).isAfter(saved.getCreatedAt());
        }
    }

    // ==================== consume() Tests ====================

    @Nested
    @DisplayName("consume()")
    class ConsumeTests {

        @Test
        @DisplayName("валидный токен → помечает used=true и возвращает его")
        void consume_ValidToken_MarksUsedAndReturns() {
            // Given
            LocalDateTime now = LocalDateTime.now();
            PasswordResetToken token = PasswordResetToken.builder()
                    .id(1L)
                    .tokenHash(sha256Hex(RAW_TOKEN))
                    .accountId(1L)
                    .accountType("USER")
                    .email("test@example.com")
                    .createdAt(now.minusMinutes(10))
                    .expiresAt(now.plusMinutes(50))
                    .used(false)
                    .build();
            when(tokenRepository.findByTokenHash(sha256Hex(RAW_TOKEN))).thenReturn(Optional.of(token));
            when(tokenRepository.save(any(PasswordResetToken.class))).thenAnswer(inv -> inv.getArgument(0));

            // When
            Optional<PasswordResetToken> result = tokenService.consume(RAW_TOKEN);

            // Then
            assertThat(result).isPresent();
            assertThat(result.get().isUsed()).isTrue();
            verify(tokenRepository).save(token);
        }

        @Test
        @DisplayName("повторный consume уже использованного токена → empty")
        void consume_AlreadyUsedToken_ReturnsEmpty() {
            // Given
            LocalDateTime now = LocalDateTime.now();
            PasswordResetToken token = PasswordResetToken.builder()
                    .id(1L)
                    .tokenHash(sha256Hex(RAW_TOKEN))
                    .accountId(1L)
                    .accountType("USER")
                    .email("test@example.com")
                    .createdAt(now.minusMinutes(10))
                    .expiresAt(now.plusMinutes(50))
                    .used(true)
                    .build();
            when(tokenRepository.findByTokenHash(sha256Hex(RAW_TOKEN))).thenReturn(Optional.of(token));

            // When
            Optional<PasswordResetToken> result = tokenService.consume(RAW_TOKEN);

            // Then
            assertThat(result).isEmpty();
            verify(tokenRepository, never()).save(any());
        }

        @Test
        @DisplayName("истёкший токен → empty")
        void consume_ExpiredToken_ReturnsEmpty() {
            // Given
            LocalDateTime now = LocalDateTime.now();
            PasswordResetToken token = PasswordResetToken.builder()
                    .id(1L)
                    .tokenHash(sha256Hex(RAW_TOKEN))
                    .accountId(1L)
                    .accountType("USER")
                    .email("test@example.com")
                    .createdAt(now.minusHours(2))
                    .expiresAt(now.minusHours(1))
                    .used(false)
                    .build();
            when(tokenRepository.findByTokenHash(sha256Hex(RAW_TOKEN))).thenReturn(Optional.of(token));

            // When
            Optional<PasswordResetToken> result = tokenService.consume(RAW_TOKEN);

            // Then
            assertThat(result).isEmpty();
            verify(tokenRepository, never()).save(any());
        }

        @Test
        @DisplayName("несуществующий токен → empty")
        void consume_NonExistingToken_ReturnsEmpty() {
            // Given
            when(tokenRepository.findByTokenHash(sha256Hex(RAW_TOKEN))).thenReturn(Optional.empty());

            // When
            Optional<PasswordResetToken> result = tokenService.consume(RAW_TOKEN);

            // Then
            assertThat(result).isEmpty();
            verify(tokenRepository, never()).save(any());
        }
    }

    // ==================== isValid() Tests ====================

    @Nested
    @DisplayName("isValid()")
    class IsValidTests {

        @Test
        @DisplayName("возвращает true для валидного токена")
        void isValid_ValidToken_ReturnsTrue() {
            // Given
            LocalDateTime now = LocalDateTime.now();
            PasswordResetToken token = PasswordResetToken.builder()
                    .tokenHash(sha256Hex(RAW_TOKEN))
                    .accountId(1L)
                    .accountType("USER")
                    .email("test@example.com")
                    .createdAt(now.minusMinutes(10))
                    .expiresAt(now.plusMinutes(50))
                    .used(false)
                    .build();
            when(tokenRepository.findByTokenHash(sha256Hex(RAW_TOKEN))).thenReturn(Optional.of(token));

            // When
            boolean result = tokenService.isValid(RAW_TOKEN);

            // Then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("возвращает false для использованного токена")
        void isValid_UsedToken_ReturnsFalse() {
            // Given
            LocalDateTime now = LocalDateTime.now();
            PasswordResetToken token = PasswordResetToken.builder()
                    .tokenHash(sha256Hex(RAW_TOKEN))
                    .accountId(1L)
                    .accountType("USER")
                    .email("test@example.com")
                    .createdAt(now.minusMinutes(10))
                    .expiresAt(now.plusMinutes(50))
                    .used(true)
                    .build();
            when(tokenRepository.findByTokenHash(sha256Hex(RAW_TOKEN))).thenReturn(Optional.of(token));

            // When
            boolean result = tokenService.isValid(RAW_TOKEN);

            // Then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("возвращает false для истёкшего токена")
        void isValid_ExpiredToken_ReturnsFalse() {
            // Given
            LocalDateTime now = LocalDateTime.now();
            PasswordResetToken token = PasswordResetToken.builder()
                    .tokenHash(sha256Hex(RAW_TOKEN))
                    .accountId(1L)
                    .accountType("USER")
                    .email("test@example.com")
                    .createdAt(now.minusHours(2))
                    .expiresAt(now.minusHours(1))
                    .used(false)
                    .build();
            when(tokenRepository.findByTokenHash(sha256Hex(RAW_TOKEN))).thenReturn(Optional.of(token));

            // When
            boolean result = tokenService.isValid(RAW_TOKEN);

            // Then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("возвращает false для несуществующего токена")
        void isValid_NonExistingToken_ReturnsFalse() {
            // Given
            when(tokenRepository.findByTokenHash(sha256Hex(RAW_TOKEN))).thenReturn(Optional.empty());

            // When
            boolean result = tokenService.isValid(RAW_TOKEN);

            // Then
            assertThat(result).isFalse();
        }
    }
}
