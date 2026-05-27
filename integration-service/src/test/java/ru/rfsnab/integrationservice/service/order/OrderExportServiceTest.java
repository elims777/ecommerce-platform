package ru.rfsnab.integrationservice.service.order;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import ru.rfsnab.integrationservice.BaseIntegrationTest;
import ru.rfsnab.integrationservice.model.PendingOrder;
import ru.rfsnab.integrationservice.repository.PendingOrderRepository;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("OrderExportService")
class OrderExportServiceTest extends BaseIntegrationTest {

    @Autowired
    private OrderExportService orderExportService;

    @Autowired
    private PendingOrderRepository pendingOrderRepository;

    @BeforeEach
    void cleanup() {
        pendingOrderRepository.deleteAll();
    }

    private PendingOrder savePendingOrder(String orderId, String orderNumber) {
        String orderData = """
                {
                    "orderId": "%s",
                    "orderNumber": "%s",
                    "createdAt": "2026-03-20T10:00:00",
                    "userId": 100,
                    "customerEmail": "test@email.com",
                    "recipientName": "Иванов Иван",
                    "recipientPhone": "+79001234567",
                    "deliveryMethod": "SUPPLIER_DELIVERY",
                    "city": "Сыктывкар",
                    "street": "Октябрьский проспект",
                    "building": "1",
                    "paymentMethod": "CARD",
                    "totalAmount": 15000.00,
                    "comment": "Тестовый заказ",
                    "items": [
                        {
                            "productId": 1,
                            "externalId": "ext-001",
                            "productName": "Перчатки нитриловые L",
                            "quantity": 10,
                            "price": 250.50
                        },
                        {
                            "productId": 2,
                            "externalId": "ext-002",
                            "productName": "Каска строительная",
                            "quantity": 5,
                            "price": 890.00
                        }
                    ]
                }
                """.formatted(orderId, orderNumber);

        return pendingOrderRepository.save(PendingOrder.builder()
                .orderId(orderId)
                .orderData(orderData)
                .externalId(orderNumber)
                .build());
    }

    @Nested
    @DisplayName("exportPendingOrders")
    class ExportTests {

        @Test
        @DisplayName("формирует XML с заказами")
        void shouldExportOrdersAsXml() {
            savePendingOrder("uuid-001", "ORD-00001");
            savePendingOrder("uuid-002", "ORD-00002");

            String xml = orderExportService.exportPendingOrders();

            assertThat(xml).contains("КоммерческаяИнформация");
            assertThat(xml).contains("uuid-001");
            assertThat(xml).contains("uuid-002");
            assertThat(xml).contains("ORD-00001");
            assertThat(xml).contains("Перчатки нитриловые L");
            assertThat(xml).contains("ext-001");
        }

        @Test
        @DisplayName("включает контрагента с ФИО и контактами")
        void shouldIncludeContragent() {
            savePendingOrder("uuid-003", "ORD-00003");

            String xml = orderExportService.exportPendingOrders();

            assertThat(xml).contains("Контрагент");
            assertThat(xml).contains("Иванов Иван");
            assertThat(xml).contains("test@email.com");
        }

        @Test
        @DisplayName("включает реквизиты: способ доставки и оплаты")
        void shouldIncludeRequisites() {
            savePendingOrder("uuid-004", "ORD-00004");

            String xml = orderExportService.exportPendingOrders();

            assertThat(xml).contains("Способ доставки");
            assertThat(xml).contains("SUPPLIER_DELIVERY");
            assertThat(xml).contains("Способ оплаты");
            assertThat(xml).contains("CARD");
        }

        @Test
        @DisplayName("возвращает пустой XML если нет заказов")
        void shouldReturnEmptyXmlWhenNoOrders() {
            String xml = orderExportService.exportPendingOrders();

            assertThat(xml).contains("КоммерческаяИнформация");
            assertThat(xml).doesNotContain("Документ");
        }

        @Test
        @DisplayName("не включает уже exported заказы")
        void shouldSkipExportedOrders() {
            PendingOrder exported = savePendingOrder("uuid-005", "ORD-00005");
            exported.setExported(true);
            exported.setExportedAt(LocalDateTime.now());
            pendingOrderRepository.save(exported);

            savePendingOrder("uuid-006", "ORD-00006");

            String xml = orderExportService.exportPendingOrders();

            assertThat(xml).doesNotContain("uuid-005");
            assertThat(xml).contains("uuid-006");
        }
    }

    @Nested
    @DisplayName("markOrdersAsExported")
    class MarkExportedTests {

        @Test
        @DisplayName("помечает все непереданные заказы как exported")
        void shouldMarkAllAsExported() {
            savePendingOrder("uuid-007", "ORD-00007");
            savePendingOrder("uuid-008", "ORD-00008");

            orderExportService.markOrdersAsExported();

            List<PendingOrder> all = pendingOrderRepository.findAll();
            assertThat(all).allMatch(PendingOrder::isExported);
            assertThat(all).allMatch(o -> o.getExportedAt() != null);
        }

        @Test
        @DisplayName("не трогает уже exported заказы")
        void shouldNotTouchAlreadyExported() {
            PendingOrder existing = savePendingOrder("uuid-009", "ORD-00009");
            existing.setExported(true);
            LocalDateTime previousExportTime = LocalDateTime.of(2026, 3, 1, 10, 0);
            existing.setExportedAt(previousExportTime);
            pendingOrderRepository.save(existing);

            savePendingOrder("uuid-010", "ORD-00010");

            orderExportService.markOrdersAsExported();

            PendingOrder reloaded = pendingOrderRepository.findAll().stream()
                    .filter(o -> "uuid-009".equals(o.getOrderId()))
                    .findFirst().orElseThrow();

            // exported_at не перезаписался
            assertThat(reloaded.getExportedAt()).isEqualTo(previousExportTime);
        }
    }
}
