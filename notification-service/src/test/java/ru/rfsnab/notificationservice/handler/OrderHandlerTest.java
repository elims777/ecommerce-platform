package ru.rfsnab.notificationservice.handler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import ru.rfsnab.notificationservice.models.OrderEvent;
import ru.rfsnab.notificationservice.service.EmailService;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrderHandler unit tests")
public class OrderHandlerTest {
    @Mock
    private EmailService emailService;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    private OrderHandler handler;

    private static final String TOPIC = "order-events";
    private static final String EMAIL = "customer@example.com";
    private static final String ORDER_NUMBER = "ORD-2026-00001";
    private static final BigDecimal TOTAL = new BigDecimal("15000.00");

    @BeforeEach
    void setUp() {
        handler = new OrderHandler(objectMapper, emailService);
        ReflectionTestUtils.setField(handler, "topic", TOPIC);
    }

    private OrderEvent createOrderEvent(String eventType, String status) {
        return new OrderEvent(
                eventType,
                UUID.randomUUID(),
                ORDER_NUMBER,
                1L,
                status,
                TOTAL,
                EMAIL,
                LocalDateTime.now()
        );
    }

    private String toJson(OrderEvent event) throws JsonProcessingException {
        return objectMapper.writeValueAsString(event);
    }

    // ==================== supports() ====================

    @Nested
    @DisplayName("supports()")
    class SupportsTests {

        @Test
        @DisplayName("order-events + любой eventType → true")
        void supports_OrderTopic_ReturnsTrue() {
            assertThat(handler.supports("order-events", "ORDER_CREATED")).isTrue();
            assertThat(handler.supports("order-events", "ORDER_PAID")).isTrue();
            assertThat(handler.supports("order-events", "ANYTHING")).isTrue();
        }

        @Test
        @DisplayName("другой topic → false")
        void supports_WrongTopic_ReturnsFalse() {
            assertThat(handler.supports("user-events", "ORDER_CREATED")).isFalse();
        }
    }

    // ==================== handle() — ORDER_CREATED ====================

    @Nested
    @DisplayName("ORDER_CREATED")
    class OrderCreatedTests {

        @Test
        @DisplayName("вызывает sendOrderCreatedEmail с правильными параметрами")
        void handle_OrderCreated_SendsCorrectEmail() throws Exception {
            // Given
            OrderEvent event = createOrderEvent("ORDER_CREATED", "Создан");

            // When
            handler.handle(toJson(event));

            // Then
            verify(emailService).sendOrderCreatedEmail(EMAIL, ORDER_NUMBER, TOTAL);
            verifyNoMoreInteractions(emailService);
        }
    }

    // ==================== handle() — ORDER_PAID ====================

    @Nested
    @DisplayName("ORDER_PAID")
    class OrderPaidTests {

        @Test
        @DisplayName("вызывает sendOrderPaidEmail с правильными параметрами")
        void handle_OrderPaid_SendsCorrectEmail() throws Exception {
            // Given
            OrderEvent event = createOrderEvent("ORDER_PAID", "Оплачен");

            // When
            handler.handle(toJson(event));

            // Then
            verify(emailService).sendOrderPaidEmail(EMAIL, ORDER_NUMBER, TOTAL);
            verifyNoMoreInteractions(emailService);
        }
    }

    // ==================== handle() — ORDER_CANCELLED ====================

    @Nested
    @DisplayName("ORDER_CANCELLED")
    class OrderCancelledTests {

        @Test
        @DisplayName("вызывает sendOrderCancelledEmail с правильными параметрами")
        void handle_OrderCancelled_SendsCorrectEmail() throws Exception {
            // Given
            OrderEvent event = createOrderEvent("ORDER_CANCELLED", "Отменён");

            // When
            handler.handle(toJson(event));

            // Then
            verify(emailService).sendOrderCancelledEmail(EMAIL, ORDER_NUMBER);
            verifyNoMoreInteractions(emailService);
        }
    }

    // ==================== handle() — ORDER_STATUS_CHANGED ====================

    @Nested
    @DisplayName("ORDER_STATUS_CHANGED")
    class OrderStatusChangedTests {

        @Test
        @DisplayName("вызывает sendOrderStatusChangedEmail с правильным статусом")
        void handle_StatusChanged_SendsCorrectEmail() throws Exception {
            // Given
            OrderEvent event = createOrderEvent("ORDER_STATUS_CHANGED", "Передан в доставку");

            // When
            handler.handle(toJson(event));

            // Then
            verify(emailService).sendOrderStatusChangedEmail(EMAIL, ORDER_NUMBER, "Передан в доставку");
            verifyNoMoreInteractions(emailService);
        }
    }

    // ==================== handle() — edge cases ====================

    @Nested
    @DisplayName("Edge cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("неизвестный eventType → не вызывает emailService")
        void handle_UnknownEventType_DoesNotSendEmail() throws Exception {
            // Given
            OrderEvent event = createOrderEvent("ORDER_UNKNOWN", "Неизвестный");

            // When
            handler.handle(toJson(event));

            // Then
            verifyNoInteractions(emailService);
        }

        @Test
        @DisplayName("невалидный JSON → не падает, не вызывает emailService")
        void handle_InvalidJson_LogsErrorAndContinues() {
            // When
            handler.handle("broken json");

            // Then
            verifyNoInteractions(emailService);
        }

        @Test
        @DisplayName("ошибка отправки email → не падает")
        void handle_EmailFails_LogsErrorAndContinues() throws Exception {
            // Given
            OrderEvent event = createOrderEvent("ORDER_CREATED", "Создан");
            doThrow(new RuntimeException("SMTP error"))
                    .when(emailService).sendOrderCreatedEmail(anyString(), anyString(), any(BigDecimal.class));

            // When — не должно выбрасывать исключение
            handler.handle(toJson(event));

            // Then
            verify(emailService).sendOrderCreatedEmail(anyString(), anyString(), any(BigDecimal.class));
        }
    }
}
