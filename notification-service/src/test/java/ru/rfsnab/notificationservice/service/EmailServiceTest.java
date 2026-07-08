package ru.rfsnab.notificationservice.service;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import ru.rfsnab.notificationservice.models.OrderEvent;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("EmailService Unit Tests")
class EmailServiceTest {

    @Mock
    private JavaMailSender mailSender;

    @Mock
    private TemplateEngine templateEngine;

    @InjectMocks
    private EmailService emailService;

    private static final String FROM_EMAIL = "noreply@rfsnab.ru";
    private static final String VERIFICATION_URL = "http://localhost:9000/verify-email";
    private static final String MANAGER_EMAIL = "manager@rfsnab.ru";
    private static final String FRONTEND_URL = "http://localhost:5173";

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(emailService, "fromEmail", FROM_EMAIL);
        ReflectionTestUtils.setField(emailService, "verificationBaseUrl", VERIFICATION_URL);
        ReflectionTestUtils.setField(emailService, "managerEmail", MANAGER_EMAIL);
        ReflectionTestUtils.setField(emailService, "frontendUrl", FRONTEND_URL);

        lenient().when(mailSender.createMimeMessage()).thenReturn(new MimeMessage((Session) null));
        lenient().when(templateEngine.process(anyString(), any(Context.class))).thenReturn("<html>OK</html>");
    }

    private OrderEvent createOrderEvent() {
        return new OrderEvent(
                "ORDER_CREATED",
                UUID.randomUUID(),
                "ORD-001",
                1L,
                "Создан",
                new BigDecimal("15000.00"),
                "customer@example.com",
                "Иванов Иван",
                "+79001234567",
                LocalDateTime.now(),
                List.of(new OrderEvent.OrderItemLine("Товар 1", 2, new BigDecimal("7500.00"), null)),
                "PICKUP",
                "CARD",
                null,
                new OrderEvent.DeliveryAddressDto("Москва", "Ленина", "1", "10", "123456", "+79001234567", "Иванов Иван"),
                "B2C",
                null,
                null,
                null
        );
    }

    @Nested
    @DisplayName("sendVerificationEmail()")
    class SendVerificationEmailTests {

        @Test
        @DisplayName("отправляет письмо по шаблону verification")
        void sendVerificationEmail_Success_SendsHtmlEmail() {
            emailService.sendVerificationEmail("user@example.com", "Иван", "verification-token-123");

            verify(mailSender).send(any(MimeMessage.class));
            verify(templateEngine).process(eq("mail/verification"), any(Context.class));
        }
    }

    @Nested
    @DisplayName("sendOrderCreatedEmail()")
    class SendOrderCreatedEmailTests {

        @Test
        @DisplayName("отправляет письмо по шаблону order-created")
        void sendOrderCreatedEmail_SendsHtmlEmail() {
            emailService.sendOrderCreatedEmail(createOrderEvent());

            verify(mailSender).send(any(MimeMessage.class));
            verify(templateEngine).process(eq("mail/order-created"), any(Context.class));
        }
    }

    @Nested
    @DisplayName("sendManagerOrderNotification()")
    class SendManagerOrderNotificationTests {

        @Test
        @DisplayName("отправляет письмо по шаблону manager-new-order")
        void sendManagerOrderNotification_SendsHtmlEmail() {
            emailService.sendManagerOrderNotification(createOrderEvent());

            verify(mailSender).send(any(MimeMessage.class));
            verify(templateEngine).process(eq("mail/manager-new-order"), any(Context.class));
        }
    }

    @Nested
    @DisplayName("sendOrderPaidEmail()")
    class SendOrderPaidEmailTests {

        @Test
        @DisplayName("отправляет письмо по шаблону order-paid")
        void sendOrderPaidEmail_SendsHtmlEmail() {
            emailService.sendOrderPaidEmail("customer@example.com", "ORD-001", new BigDecimal("15000.00"));

            verify(mailSender).send(any(MimeMessage.class));
            verify(templateEngine).process(eq("mail/order-paid"), any(Context.class));
        }
    }

    @Nested
    @DisplayName("sendOrderCancelledEmail()")
    class SendOrderCancelledEmailTests {

        @Test
        @DisplayName("отправляет письмо по шаблону order-cancelled")
        void sendOrderCancelledEmail_SendsHtmlEmail() {
            emailService.sendOrderCancelledEmail("customer@example.com", "ORD-001");

            verify(mailSender).send(any(MimeMessage.class));
            verify(templateEngine).process(eq("mail/order-cancelled"), any(Context.class));
        }
    }

    @Nested
    @DisplayName("sendOrderStatusChangedEmail()")
    class SendOrderStatusChangedEmailTests {

        @Test
        @DisplayName("отправляет письмо по шаблону order-status-changed")
        void sendOrderStatusChangedEmail_SendsHtmlEmail() {
            emailService.sendOrderStatusChangedEmail("customer@example.com", "ORD-001", "Передан в доставку");

            verify(mailSender).send(any(MimeMessage.class));
            verify(templateEngine).process(eq("mail/order-status-changed"), any(Context.class));
        }
    }

    @Nested
    @DisplayName("обработка ошибок")
    class ErrorHandlingTests {

        @Test
        @DisplayName("ошибка отправки не пробрасывается наружу")
        void send_MailSendException_DoesNotThrow() {
            doThrow(new RuntimeException("SMTP connection failed"))
                    .when(mailSender).send(any(MimeMessage.class));

            emailService.sendVerificationEmail("user@example.com", "Анна", "token-456");

            verify(mailSender).send(any(MimeMessage.class));
        }
    }
}
