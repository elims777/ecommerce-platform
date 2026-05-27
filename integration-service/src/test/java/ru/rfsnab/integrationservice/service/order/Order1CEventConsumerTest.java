package ru.rfsnab.integrationservice.service.order;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import ru.rfsnab.integrationservice.BaseIntegrationTest;
import ru.rfsnab.integrationservice.model.PendingOrder;
import ru.rfsnab.integrationservice.repository.PendingOrderRepository;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Тесты Order1CEventConsumer.
 * Вызываем consume() напрямую (unit-стиль) — без реального Kafka,
 * т.к. Kafka container не поднят в integration-service тестах.
 */
@DisplayName("Order1CEventConsumer")
class Order1CEventConsumerTest extends BaseIntegrationTest {

    @Autowired
    private Order1CEventConsumer consumer;

    @Autowired
    private PendingOrderRepository pendingOrderRepository;

    @BeforeEach
    void cleanup() {
        pendingOrderRepository.deleteAll();
    }

    private String buildOrderMessage(String orderId, String orderNumber) {
        return """
                {
                    "orderId": "%s",
                    "orderNumber": "%s",
                    "createdAt": "2026-03-20T10:00:00",
                    "userId": 100,
                    "customerEmail": "test@email.com",
                    "totalAmount": 15000.00,
                    "items": [
                        {
                            "productId": 1,
                            "externalId": "ext-001",
                            "productName": "Перчатки",
                            "quantity": 10,
                            "price": 250.50
                        }
                    ]
                }
                """.formatted(orderId, orderNumber);
    }

    @Test
    @DisplayName("сохраняет заказ в pending_orders")
    void shouldSaveOrderToPendingOrders() {
        String message = buildOrderMessage("uuid-100", "ORD-00100");

        consumer.consume(message);

        List<PendingOrder> orders = pendingOrderRepository.findAll();
        assertThat(orders).hasSize(1);
        assertThat(orders.getFirst().getOrderId()).isEqualTo("uuid-100");
        assertThat(orders.getFirst().getExternalId()).isEqualTo("ORD-00100");
        assertThat(orders.getFirst().isExported()).isFalse();
        assertThat(orders.getFirst().getOrderData()).contains("uuid-100");
    }

    @Test
    @DisplayName("пропускает дубликат по orderId (idempotency)")
    void shouldSkipDuplicateOrder() {
        String message = buildOrderMessage("uuid-101", "ORD-00101");

        consumer.consume(message);
        consumer.consume(message); // повторное сообщение

        assertThat(pendingOrderRepository.findAll()).hasSize(1);
    }

    @Test
    @DisplayName("не падает на невалидном JSON")
    void shouldNotFailOnInvalidJson() {
        consumer.consume("это не JSON");

        assertThat(pendingOrderRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("обрабатывает несколько заказов")
    void shouldHandleMultipleOrders() {
        consumer.consume(buildOrderMessage("uuid-201", "ORD-00201"));
        consumer.consume(buildOrderMessage("uuid-202", "ORD-00202"));
        consumer.consume(buildOrderMessage("uuid-203", "ORD-00203"));

        assertThat(pendingOrderRepository.findAll()).hasSize(3);
        assertThat(pendingOrderRepository.findByExportedFalseOrderByCreatedAtAsc()).hasSize(3);
    }
}
