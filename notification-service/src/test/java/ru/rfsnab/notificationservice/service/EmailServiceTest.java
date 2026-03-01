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

import java.math.BigDecimal;

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

        @Nested
        @DisplayName("sendOrderCreatedEmail()")
        class SendOrderCreatedEmailTests {

            @Test
            @DisplayName("отправляет email с правильными данными")
            void sendOrderCreatedEmail_SendsCorrectEmail() {
                // Given
                doNothing().when(mailSender).send(any(SimpleMailMessage.class));

                // When
                emailService.sendOrderCreatedEmail("customer@example.com", "ORD-001", new BigDecimal("15000.00"));

                // Then
                verify(mailSender).send(messageCaptor.capture());
                SimpleMailMessage sent = messageCaptor.getValue();

                assertThat(sent.getFrom()).isEqualTo(FROM_EMAIL);
                assertThat(sent.getTo()).containsExactly("customer@example.com");
                assertThat(sent.getSubject()).contains("ORD-001");
                assertThat(sent.getText())
                        .contains("ORD-001")
                        .contains("15000.00")
                        .contains("менеджеру");
            }
        }

        @Nested
        @DisplayName("sendOrderPaidEmail()")
        class SendOrderPaidEmailTests {

            @Test
            @DisplayName("отправляет email с правильными данными")
            void sendOrderPaidEmail_SendsCorrectEmail() {
                // Given
                doNothing().when(mailSender).send(any(SimpleMailMessage.class));

                // When
                emailService.sendOrderPaidEmail("customer@example.com", "ORD-001", new BigDecimal("15000.00"));

                // Then
                verify(mailSender).send(messageCaptor.capture());
                SimpleMailMessage sent = messageCaptor.getValue();

                assertThat(sent.getSubject()).contains("ORD-001").contains("подтверждена");
                assertThat(sent.getText())
                        .contains("ORD-001")
                        .contains("15000.00")
                        .contains("в работу");
            }
        }

        @Nested
        @DisplayName("sendOrderCancelledEmail()")
        class SendOrderCancelledEmailTests {

            @Test
            @DisplayName("отправляет email с правильными данными")
            void sendOrderCancelledEmail_SendsCorrectEmail() {
                // Given
                doNothing().when(mailSender).send(any(SimpleMailMessage.class));

                // When
                emailService.sendOrderCancelledEmail("customer@example.com", "ORD-001");

                // Then
                verify(mailSender).send(messageCaptor.capture());
                SimpleMailMessage sent = messageCaptor.getValue();

                assertThat(sent.getSubject()).contains("ORD-001").contains("отменён");
                assertThat(sent.getText())
                        .contains("ORD-001")
                        .contains("возвращены");
            }
        }

        @Nested
        @DisplayName("sendOrderStatusChangedEmail()")
        class SendOrderStatusChangedEmailTests {

            @Test
            @DisplayName("отправляет email с правильным статусом")
            void sendOrderStatusChangedEmail_SendsCorrectEmail() {
                // Given
                doNothing().when(mailSender).send(any(SimpleMailMessage.class));

                // When
                emailService.sendOrderStatusChangedEmail("customer@example.com", "ORD-001", "Передан в доставку");

                // Then
                verify(mailSender).send(messageCaptor.capture());
                SimpleMailMessage sent = messageCaptor.getValue();

                assertThat(sent.getSubject()).contains("ORD-001").contains("обновлён");
                assertThat(sent.getText())
                        .contains("ORD-001")
                        .contains("Передан в доставку");
            }
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
