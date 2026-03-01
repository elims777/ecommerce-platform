package ru.rfsnab.notificationservice.handler;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("NotificationRouter Unit Tests")
class NotificationRouterTest {

    private TestHandler userHandler;
    private TestHandler orderHandler;
    private NotificationRouter router;

    @BeforeEach
    void setUp() {
        userHandler = new TestHandler("user-events", "USER_REGISTERED");
        orderHandler = new TestHandler("order-events", null); // null = весь топик

        router = new NotificationRouter(List.of(userHandler, orderHandler));
        router.init();
    }

    @Test
    @DisplayName("делегирует USER_REGISTERED → userHandler")
    void route_UserRegistered_DelegatesToUserHandler() {
        router.route("user-events", "USER_REGISTERED", "{\"json\":true}");

        assertThat(userHandler.receivedEvents).containsExactly("{\"json\":true}");
        assertThat(orderHandler.receivedEvents).isEmpty();
    }

    @Test
    @DisplayName("делегирует ORDER_CREATED → orderHandler")
    void route_OrderCreated_DelegatesToOrderHandler() {
        router.route("order-events", "ORDER_CREATED", "{\"json\":true}");

        assertThat(orderHandler.receivedEvents).containsExactly("{\"json\":true}");
        assertThat(userHandler.receivedEvents).isEmpty();
    }

    @Test
    @DisplayName("неизвестное событие → ни один handler не вызван")
    void route_UnknownEvent_NoHandlerCalled() {
        router.route("payment-events", "PAYMENT_DONE", "{}");

        assertThat(userHandler.receivedEvents).isEmpty();
        assertThat(orderHandler.receivedEvents).isEmpty();
    }

    /**
     * Простая тестовая реализация NotificationHandler.
     * eventType == null означает "весь топик" (как OrderHandler).
     */
    private static class TestHandler implements NotificationHandler {
        private final String topic;
        private final String eventType;
        final List<String> receivedEvents = new ArrayList<>();

        TestHandler(String topic, String eventType) {
            this.topic = topic;
            this.eventType = eventType;
        }

        @Override
        public boolean supports(String topic, String eventType) {
            return this.topic.equals(topic)
                    && (this.eventType == null || this.eventType.equals(eventType));
        }

        @Override
        public void handle(String eventJson) {
            receivedEvents.add(eventJson);
        }
    }
}