package ru.rfsnab.notificationservice.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.rfsnab.notificationservice.models.EmailVerificationToken;
import ru.rfsnab.notificationservice.models.VerificationResponse;
import ru.rfsnab.notificationservice.repository.EmailVerificationTokenRepository;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("EmailVerificationTokenService Unit Tests")
class EmailVerificationTokenServiceTest {

    @Mock
    private EmailVerificationTokenRepository tokenRepository;

    @InjectMocks
    private EmailVerificationTokenService tokenService;

    private EmailVerificationToken validToken;
    private EmailVerificationToken expiredToken;
    private EmailVerificationToken verifiedToken;

    @BeforeEach
    void setUp() {
        LocalDateTime now = LocalDateTime.now();

        validToken = EmailVerificationToken.builder()
                .id(1L)
                .token("valid-token-123")
                .userId(100L)
                .email("test@example.com")
                .createdAt(now.minusMinutes(30))
                .expiresAt(now.plusMinutes(30))
                .verified(false)
                .build();

        expiredToken = EmailVerificationToken.builder()
                .id(2L)
                .token("expired-token-456")
                .userId(101L)
                .email("expired@example.com")
                .createdAt(now.minusHours(2))
                .expiresAt(now.minusHours(1))
                .verified(false)
                .build();

        verifiedToken = EmailVerificationToken.builder()
                .id(3L)
                .token("verified-token-789")
                .userId(102L)
                .email("verified@example.com")
                .createdAt(now.minusMinutes(30))
                .expiresAt(now.plusMinutes(30))
                .verified(true)
                .build();
    }

    // ==================== save() Tests ====================

    @Nested
    @DisplayName("save()")
    class SaveTests {

        @Test
        @DisplayName("сохраняет токен и возвращает его")
        void save_ValidToken_ReturnsSavedToken() {
            // Given
            when(tokenRepository.save(any(EmailVerificationToken.class))).thenReturn(validToken);

            // When
            EmailVerificationToken result = tokenService.save(validToken);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getToken()).isEqualTo("valid-token-123");
            verify(tokenRepository).save(validToken);
        }
    }

    // ==================== findByToken() Tests ====================

    @Nested
    @DisplayName("findByToken()")
    class FindByTokenTests {

        @Test
        @DisplayName("находит существующий токен")
        void findByToken_ExistingToken_ReturnsToken() {
            // Given
            when(tokenRepository.findByToken("valid-token-123")).thenReturn(Optional.of(validToken));

            // When
            Optional<EmailVerificationToken> result = tokenService.findByToken("valid-token-123");

            // Then
            assertThat(result).isPresent();
            assertThat(result.get().getEmail()).isEqualTo("test@example.com");
        }

        @Test
        @DisplayName("возвращает empty для несуществующего токена")
        void findByToken_NonExistingToken_ReturnsEmpty() {
            // Given
            when(tokenRepository.findByToken("non-existing")).thenReturn(Optional.empty());

            // When
            Optional<EmailVerificationToken> result = tokenService.findByToken("non-existing");

            // Then
            assertThat(result).isEmpty();
        }
    }

    // ==================== findByUserId() Tests ====================

    @Nested
    @DisplayName("findByUserId()")
    class FindByUserIdTests {

        @Test
        @DisplayName("находит токен по userId")
        void findByUserId_ExistingUser_ReturnsToken() {
            // Given
            when(tokenRepository.findByUserId(100L)).thenReturn(Optional.of(validToken));

            // When
            Optional<EmailVerificationToken> result = tokenService.findByUserId(100L);

            // Then
            assertThat(result).isPresent();
            assertThat(result.get().getUserId()).isEqualTo(100L);
        }
    }

    // ==================== isExpired() Tests ====================

    @Nested
    @DisplayName("isExpired()")
    class IsExpiredTests {

        @Test
        @DisplayName("возвращает false для не истёкшего токена")
        void isExpired_ValidToken_ReturnsFalse() {
            // When
            boolean result = tokenService.isExpired(validToken);

            // Then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("возвращает true для истёкшего токена")
        void isExpired_ExpiredToken_ReturnsTrue() {
            // When
            boolean result = tokenService.isExpired(expiredToken);

            // Then
            assertThat(result).isTrue();
        }
    }

    // ==================== isValid() Tests ====================

    @Nested
    @DisplayName("isValid()")
    class IsValidTests {

        @Test
        @DisplayName("возвращает true для валидного токена")
        void isValid_ValidToken_ReturnsTrue() {
            // When
            boolean result = tokenService.isValid(validToken);

            // Then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("возвращает false для истёкшего токена")
        void isValid_ExpiredToken_ReturnsFalse() {
            // When
            boolean result = tokenService.isValid(expiredToken);

            // Then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("возвращает false для уже верифицированного токена")
        void isValid_VerifiedToken_ReturnsFalse() {
            // When
            boolean result = tokenService.isValid(verifiedToken);

            // Then
            assertThat(result).isFalse();
        }
    }

    // ==================== markAsVerified() Tests ====================

    @Nested
    @DisplayName("markAsVerified()")
    class MarkAsVerifiedTests {

        @Test
        @DisplayName("устанавливает verified=true и сохраняет")
        void markAsVerified_ValidToken_SetsVerifiedAndSaves() {
            // Given
            when(tokenRepository.save(any(EmailVerificationToken.class))).thenReturn(validToken);

            // When
            tokenService.markAsVerified(validToken);

            // Then
            assertThat(validToken.isVerified()).isTrue();
            verify(tokenRepository).save(validToken);
        }
    }

    // ==================== existsByToken() Tests ====================

    @Nested
    @DisplayName("existsByToken()")
    class ExistsByTokenTests {

        @Test
        @DisplayName("возвращает true для существующего токена")
        void existsByToken_ExistingToken_ReturnsTrue() {
            // Given
            when(tokenRepository.existsByToken("valid-token-123")).thenReturn(true);

            // When
            boolean result = tokenService.existsByToken("valid-token-123");

            // Then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("возвращает false для несуществующего токена")
        void existsByToken_NonExistingToken_ReturnsFalse() {
            // Given
            when(tokenRepository.existsByToken("non-existing")).thenReturn(false);

            // When
            boolean result = tokenService.existsByToken("non-existing");

            // Then
            assertThat(result).isFalse();
        }
    }

    // ==================== verifyToken() Tests ====================

    @Nested
    @DisplayName("verifyToken()")
    class VerifyTokenTests {

        @Test
        @DisplayName("успешная верификация валидного токена")
        void verifyToken_ValidToken_ReturnsSuccessResponse() {
            // Given
            when(tokenRepository.findByToken("valid-token-123")).thenReturn(Optional.of(validToken));
            when(tokenRepository.save(any(EmailVerificationToken.class))).thenReturn(validToken);

            // When
            VerificationResponse result = tokenService.verifyToken("valid-token-123");

            // Then
            assertThat(result.isValid()).isTrue();
            assertThat(result.getUserId()).isEqualTo(100L);
            assertThat(result.getEmail()).isEqualTo("test@example.com");
            assertThat(result.getMessage()).isEqualTo("Email verified successfully");
            verify(tokenRepository).save(validToken);
        }

        @Test
        @DisplayName("токен не найден → возвращает ошибку")
        void verifyToken_TokenNotFound_ReturnsError() {
            // Given
            when(tokenRepository.findByToken("non-existing")).thenReturn(Optional.empty());

            // When
            VerificationResponse result = tokenService.verifyToken("non-existing");

            // Then
            assertThat(result.isValid()).isFalse();
            assertThat(result.getMessage()).isEqualTo("Token not found");
            verify(tokenRepository, never()).save(any());
        }

        @Test
        @DisplayName("токен уже верифицирован → возвращает ошибку")
        void verifyToken_AlreadyVerified_ReturnsError() {
            // Given
            when(tokenRepository.findByToken("verified-token-789")).thenReturn(Optional.of(verifiedToken));

            // When
            VerificationResponse result = tokenService.verifyToken("verified-token-789");

            // Then
            assertThat(result.isValid()).isFalse();
            assertThat(result.getMessage()).isEqualTo("Email already verified");
            verify(tokenRepository, never()).save(any());
        }

        @Test
        @DisplayName("токен истёк → возвращает ошибку")
        void verifyToken_ExpiredToken_ReturnsError() {
            // Given
            when(tokenRepository.findByToken("expired-token-456")).thenReturn(Optional.of(expiredToken));

            // When
            VerificationResponse result = tokenService.verifyToken("expired-token-456");

            // Then
            assertThat(result.isValid()).isFalse();
            assertThat(result.getMessage()).isEqualTo("Token expired");
            verify(tokenRepository, never()).save(any());
        }
    }
}
