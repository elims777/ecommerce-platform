package ru.rfsnab.notificationservice.handler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import ru.rfsnab.notificationservice.models.EmailVerificationToken;
import ru.rfsnab.notificationservice.models.UserEvent;
import ru.rfsnab.notificationservice.service.EmailService;
import ru.rfsnab.notificationservice.service.EmailVerificationTokenService;

import java.time.LocalDateTime;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserRegisteredHandler unit tests")
public class UserRegisteredHandlerTest {

    @Mock
    private EmailVerificationTokenService tokenService;

    @Mock
    private EmailService emailService;

    @Captor
    private ArgumentCaptor<EmailVerificationToken> tokenCaptor;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    private UserRegisteredHandler handler;

    private static final String TOPIC = "user-events";

    @BeforeEach
    void setUp(){
        handler = new UserRegisteredHandler(objectMapper, tokenService, emailService);
        ReflectionTestUtils.setField(handler, "topic", TOPIC);
    }

    private UserEvent createUserEvent(){
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

    private String toJson(UserEvent event) throws JsonProcessingException {
        return objectMapper.writeValueAsString(event);
    }

    @Nested
    @DisplayName("supports()")
    class SupportsTests{
        @Test
        @DisplayName("user-events + USER_REGISTERED → true")
        void supports_CorrectTopicAndEventType_ReturnsTrue() {
            assertThat(handler.supports("user-events", "USER_REGISTERED")).isTrue();
        }

        @Test
        @DisplayName("другой topic → false")
        void supports_WrongTopic_ReturnsFalse() {
            assertThat(handler.supports("order-events", "USER_REGISTERED")).isFalse();
        }

        @Test
        @DisplayName("другой eventType → false")
        void supports_WrongEventType_ReturnsFalse() {
            assertThat(handler.supports("user-events", "USER_DELETED")).isFalse();
        }
    }

    @Nested
    @DisplayName("handle()")
    class HandleTests {

        @Test
        @DisplayName("сохраняет токен и отправляет email")
        void handle_ValidEvent_SavesTokenAndSendsEmail() throws Exception {
            // Given
            UserEvent event = createUserEvent();
            when(tokenService.existsByToken("token-abc-123")).thenReturn(false);
            when(tokenService.save(any(EmailVerificationToken.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            // When
            handler.handle(toJson(event));

            // Then
            verify(tokenService).save(tokenCaptor.capture());
            verify(emailService).sendVerificationEmail("newuser@example.com", "Иван", "token-abc-123");

            EmailVerificationToken savedToken = tokenCaptor.getValue();
            assertThat(savedToken.getToken()).isEqualTo("token-abc-123");
            assertThat(savedToken.getUserId()).isEqualTo(100L);
            assertThat(savedToken.getEmail()).isEqualTo("newuser@example.com");
            assertThat(savedToken.isVerified()).isFalse();
        }

        @Test
        @DisplayName("токен имеет срок действия 1 час")
        void handle_ValidEvent_TokenExpiresInOneHour() throws Exception {
            // Given
            UserEvent event = createUserEvent();
            when(tokenService.existsByToken("token-abc-123")).thenReturn(false);
            when(tokenService.save(any(EmailVerificationToken.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            // When
            handler.handle(toJson(event));

            // Then
            verify(tokenService).save(tokenCaptor.capture());
            EmailVerificationToken savedToken = tokenCaptor.getValue();

            long hoursDiff = java.time.Duration.between(
                    savedToken.getCreatedAt(),
                    savedToken.getExpiresAt()
            ).toHours();
            assertThat(hoursDiff).isEqualTo(1);
        }

        @Test
        @DisplayName("дубликат токена → пропускает обработку")
        void handle_DuplicateToken_SkipsProcessing() throws Exception {
            // Given
            UserEvent event = createUserEvent();
            when(tokenService.existsByToken("token-abc-123")).thenReturn(true);

            // When
            handler.handle(toJson(event));

            // Then
            verify(tokenService, never()).save(any());
            verify(emailService, never()).sendVerificationEmail(anyString(), anyString(), anyString());
        }

        @Test
        @DisplayName("невалидный JSON → логирует ошибку, не падает")
        void handle_InvalidJson_LogsErrorAndContinues() {
            // When & Then — не должно выбрасывать исключение
            handler.handle("not a json");

            verify(tokenService, never()).save(any());
            verify(emailService, never()).sendVerificationEmail(anyString(), anyString(), anyString());
        }

        @Test
        @DisplayName("ошибка отправки email → логирует ошибку, не падает")
        void handle_EmailFails_LogsErrorAndContinues() throws Exception {
            // Given
            UserEvent event = createUserEvent();
            when(tokenService.existsByToken("token-abc-123")).thenReturn(false);
            when(tokenService.save(any(EmailVerificationToken.class)))
                    .thenAnswer(inv -> inv.getArgument(0));
            doThrow(new RuntimeException("SMTP error"))
                    .when(emailService).sendVerificationEmail(anyString(), anyString(), anyString());

            // When — не должно выбрасывать исключение наружу
            handler.handle(toJson(event));

            // Then — токен сохранён, email попытка была
            verify(tokenService).save(any());
            verify(emailService).sendVerificationEmail(anyString(), anyString(), anyString());
        }
    }
}
