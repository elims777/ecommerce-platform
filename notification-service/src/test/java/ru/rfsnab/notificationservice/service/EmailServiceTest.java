package ru.rfsnab.notificationservice.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("EmailService Unit Tests")
class EmailServiceTest {

    @Mock
    private JavaMailSender mailSender;

    @InjectMocks
    private EmailService emailService;

    @Captor
    private ArgumentCaptor<SimpleMailMessage> messageCaptor;

    private static final String FROM_EMAIL = "noreply@rfsnab.ru";
    private static final String VERIFICATION_URL = "http://localhost:9000/verify-email";

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(emailService, "fromEmail", FROM_EMAIL);
        ReflectionTestUtils.setField(emailService, "verificationBaseUrl", VERIFICATION_URL);
    }

    @Nested
    @DisplayName("sendVerificationEmail()")
    class SendVerificationEmailTests {

        @Test
        @DisplayName("успешно отправляет email с правильными данными")
        void sendVerificationEmail_Success_SendsCorrectEmail() {
            // Given
            String toEmail = "user@example.com";
            String firstName = "Иван";
            String token = "verification-token-123";

            doNothing().when(mailSender).send(any(SimpleMailMessage.class));

            // When
            emailService.sendVerificationEmail(toEmail, firstName, token);

            // Then
            verify(mailSender).send(messageCaptor.capture());

            SimpleMailMessage sentMessage = messageCaptor.getValue();
            assertThat(sentMessage.getFrom()).isEqualTo(FROM_EMAIL);
            assertThat(sentMessage.getTo()).containsExactly(toEmail);
            assertThat(sentMessage.getSubject()).isEqualTo("Подтверждение почтового адреса");
            assertThat(sentMessage.getText())
                    .contains(firstName)
                    .contains(VERIFICATION_URL + "?token=" + token)
                    .contains("Спасибо за регистрацию");
        }

        @Test
        @DisplayName("формирует правильную ссылку верификации")
        void sendVerificationEmail_CorrectVerificationLink() {
            // Given
            String toEmail = "user@example.com";
            String firstName = "Пётр";
            String token = "abc-123-xyz";

            doNothing().when(mailSender).send(any(SimpleMailMessage.class));

            // When
            emailService.sendVerificationEmail(toEmail, firstName, token);

            // Then
            verify(mailSender).send(messageCaptor.capture());

            String expectedLink = VERIFICATION_URL + "?token=" + token;
            assertThat(messageCaptor.getValue().getText()).contains(expectedLink);
        }

        @Test
        @DisplayName("выбрасывает RuntimeException при ошибке отправки")
        void sendVerificationEmail_MailSendException_ThrowsRuntimeException() {
            // Given
            String toEmail = "user@example.com";
            String firstName = "Анна";
            String token = "token-456";

            doThrow(new MailSendException("SMTP connection failed"))
                    .when(mailSender).send(any(SimpleMailMessage.class));

            // When & Then
            assertThatThrownBy(() -> emailService.sendVerificationEmail(toEmail, firstName, token))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Failed to send email");

            verify(mailSender).send(any(SimpleMailMessage.class));
        }

        @Test
        @DisplayName("текст письма содержит информацию о сроке действия")
        void sendVerificationEmail_ContainsExpirationInfo() {
            // Given
            String toEmail = "user@example.com";
            String firstName = "Мария";
            String token = "token-789";

            doNothing().when(mailSender).send(any(SimpleMailMessage.class));

            // When
            emailService.sendVerificationEmail(toEmail, firstName, token);

            // Then
            verify(mailSender).send(messageCaptor.capture());
            assertThat(messageCaptor.getValue().getText())
                    .contains("1 час");
        }
    }
}
