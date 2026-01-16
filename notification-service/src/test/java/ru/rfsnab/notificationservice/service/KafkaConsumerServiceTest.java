package ru.rfsnab.notificationservice.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.rfsnab.notificationservice.models.EmailVerificationToken;
import ru.rfsnab.notificationservice.models.UserEvent;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("KafkaConsumerService Unit Tests")
class KafkaConsumerServiceTest {

    @Mock
    private EmailVerificationTokenService tokenService;

    @Mock
    private EmailService emailService;

    private KafkaConsumerService kafkaConsumerService;

    @Captor
    private ArgumentCaptor<EmailVerificationToken> tokenCaptor;

    @BeforeEach
    void setUp() {
        // KafkaConsumerService имеет два поля tokenService и emailVerificationTokenService
        // которые ссылаются на один и тот же бин. Создаём вручную.
        kafkaConsumerService = new KafkaConsumerService(tokenService, emailService, tokenService);
    }

    private UserEvent createUserRegisteredEvent() {
        return UserEvent.builder()
                .eventType("USER_REGISTERED")
                .userId(100L)
                .email("newuser@example.com")
                .firstName("Иван")
                .lastName("Петров")
                .verificationToken("token-abc-123")
                .timestamp(LocalDateTime.now())
                .build();
    }

    @Nested
    @DisplayName("consumeUserEvent()")
    class ConsumeUserEventTests {

        @Test
        @DisplayName("USER_REGISTERED → сохраняет токен и отправляет email")
        void consumeUserEvent_UserRegistered_SavesTokenAndSendsEmail() {
            // Given
            UserEvent event = createUserRegisteredEvent();
            when(tokenService.existsByToken("token-abc-123")).thenReturn(false);
            when(tokenService.save(any(EmailVerificationToken.class))).thenAnswer(inv -> inv.getArgument(0));
            doNothing().when(emailService).sendVerificationEmail(anyString(), anyString(), anyString());

            // When
            kafkaConsumerService.consumeUserEvent(event);

            // Then
            verify(tokenService).save(tokenCaptor.capture());
            verify(emailService).sendVerificationEmail("newuser@example.com", "Иван", "token-abc-123");

            EmailVerificationToken savedToken = tokenCaptor.getValue();
            assertThat(savedToken.getToken()).isEqualTo("token-abc-123");
            assertThat(savedToken.getUserId()).isEqualTo(100L);
            assertThat(savedToken.getEmail()).isEqualTo("newuser@example.com");
            assertThat(savedToken.isVerified()).isFalse();
            assertThat(savedToken.getExpiresAt()).isAfter(savedToken.getCreatedAt());
        }

        @Test
        @DisplayName("USER_REGISTERED с существующим токеном → пропускает обработку")
        void consumeUserEvent_TokenAlreadyExists_SkipsProcessing() {
            // Given
            UserEvent event = createUserRegisteredEvent();
            when(tokenService.existsByToken("token-abc-123")).thenReturn(true);

            // When
            kafkaConsumerService.consumeUserEvent(event);

            // Then
            verify(tokenService, never()).save(any());
            verify(emailService, never()).sendVerificationEmail(anyString(), anyString(), anyString());
        }

        @Test
        @DisplayName("неизвестный тип события → логирует предупреждение, не падает")
        void consumeUserEvent_UnknownEventType_LogsWarningAndContinues() {
            // Given
            UserEvent event = UserEvent.builder()
                    .eventType("UNKNOWN_EVENT")
                    .userId(100L)
                    .email("test@example.com")
                    .build();

            // When & Then - не должно выбрасывать исключение
            kafkaConsumerService.consumeUserEvent(event);

            verify(tokenService, never()).save(any());
            verify(emailService, never()).sendVerificationEmail(anyString(), anyString(), anyString());
        }

        @Test
        @DisplayName("ошибка отправки email → выбрасывает RuntimeException")
        void consumeUserEvent_EmailSendingFails_ThrowsException() {
            // Given
            UserEvent event = createUserRegisteredEvent();
            when(tokenService.existsByToken("token-abc-123")).thenReturn(false);
            when(tokenService.save(any(EmailVerificationToken.class))).thenAnswer(inv -> inv.getArgument(0));
            doThrow(new RuntimeException("SMTP error"))
                    .when(emailService).sendVerificationEmail(anyString(), anyString(), anyString());

            // When & Then
            assertThatThrownBy(() -> kafkaConsumerService.consumeUserEvent(event))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Email sending failed");

            // Токен всё равно сохраняется до попытки отправки email
            verify(tokenService).save(any());
        }

        @Test
        @DisplayName("токен имеет правильный срок действия (1 час)")
        void consumeUserEvent_TokenExpiresInOneHour() {
            // Given
            UserEvent event = createUserRegisteredEvent();
            when(tokenService.existsByToken("token-abc-123")).thenReturn(false);
            when(tokenService.save(any(EmailVerificationToken.class))).thenAnswer(inv -> inv.getArgument(0));
            doNothing().when(emailService).sendVerificationEmail(anyString(), anyString(), anyString());

            LocalDateTime beforeTest = LocalDateTime.now();

            // When
            kafkaConsumerService.consumeUserEvent(event);

            // Then
            verify(tokenService).save(tokenCaptor.capture());
            EmailVerificationToken savedToken = tokenCaptor.getValue();

            // expiresAt должен быть примерно через 1 час от createdAt
            long hoursDiff = java.time.Duration.between(
                    savedToken.getCreatedAt(),
                    savedToken.getExpiresAt()
            ).toHours();
            assertThat(hoursDiff).isEqualTo(1);
        }
    }
}
