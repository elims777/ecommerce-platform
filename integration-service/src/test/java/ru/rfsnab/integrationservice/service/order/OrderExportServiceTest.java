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

    private PendingOrder saveB2BPendingOrder(String orderId, String orderNumber) {
        String orderData = """
                {
                    "orderId": "%s",
                    "orderNumber": "%s",
                    "createdAt": "2026-03-20T10:00:00",
                    "userId": 100,
                    "customerType": "B2B",
                    "companyName": "ООО Ромашка",
                    "inn": "7701234567",
                    "customerEmail": "b2b@email.com",
                    "recipientName": "Петров Петр",
                    "recipientPhone": "+79001234567",
                    "deliveryMethod": "SUPPLIER_DELIVERY",
                    "city": "Сыктывкар",
                    "street": "Октябрьский проспект",
                    "building": "1",
                    "paymentMethod": "CARD",
                    "totalAmount": 15000.00,
                    "comment": "Тестовый B2B заказ",
                    "items": [
                        {
                            "productId": 1,
                            "externalId": "ext-001",
                            "productName": "Перчатки нитриловые L",
                            "quantity": 10,
                            "price": 250.50
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

            OrderExportService.OrderExportResult result = orderExportService.exportPendingOrders();

            assertThat(result.count()).isEqualTo(2);
            assertThat(result.xml()).contains("КоммерческаяИнформация");
            assertThat(result.xml()).contains("uuid-001");
            assertThat(result.xml()).contains("uuid-002");
            assertThat(result.xml()).contains("ORD-00001");
            assertThat(result.xml()).contains("Перчатки нитриловые L");
            assertThat(result.xml()).contains("ext-001");
        }

        @Test
        @DisplayName("включает контрагента с ФИО и контактами")
        void shouldIncludeContragent() {
            savePendingOrder("uuid-003", "ORD-00003");

            OrderExportService.OrderExportResult result = orderExportService.exportPendingOrders();

            assertThat(result.xml()).contains("Контрагент");
            assertThat(result.xml()).contains("Иванов Иван");
            assertThat(result.xml()).contains("<Фамилия>Иванов</Фамилия>");
            assertThat(result.xml()).contains("<Имя>Иван</Имя>");
            assertThat(result.xml()).contains("<Почта>test@email.com</Почта>");
            assertThat(result.xml()).doesNotContain("<Наименование>test@email.com");
        }

        @Test
        @DisplayName("B2B заказ: контрагент — организация с ИНН, без фамилии")
        void shouldIncludeCompanyContragentForB2B() {
            saveB2BPendingOrder("uuid-b2b-001", "ORD-B2B-001");

            OrderExportService.OrderExportResult result = orderExportService.exportPendingOrders();

            assertThat(result.xml()).contains("<Наименование>ООО Ромашка</Наименование>");
            assertThat(result.xml()).contains("<ИНН>7701234567</ИНН>");
            assertThat(result.xml()).doesNotContain("<Фамилия>");
        }

        @Test
        @DisplayName("Ид контрагента не заполняется (матчинг в 1С по ИНН/наименованию)")
        void shouldNotSetContragentId() {
            savePendingOrder("uuid-003", "ORD-00003");

            OrderExportService.OrderExportResult result = orderExportService.exportPendingOrders();

            // userId=100 нигде не должен фигурировать как Ид (документа или товара - строковый UUID/externalId)
            assertThat(result.xml()).doesNotContain("<Ид>100</Ид>");
        }

        @Test
        @DisplayName("включает реквизиты: способ доставки и оплаты")
        void shouldIncludeRequisites() {
            savePendingOrder("uuid-004", "ORD-00004");

            OrderExportService.OrderExportResult result = orderExportService.exportPendingOrders();

            assertThat(result.xml()).contains("Способ доставки");
            assertThat(result.xml()).contains("SUPPLIER_DELIVERY");
            assertThat(result.xml()).contains("Способ оплаты");
            assertThat(result.xml()).contains("CARD");
        }

        @Test
        @DisplayName("возвращает пустой XML если нет заказов")
        void shouldReturnEmptyXmlWhenNoOrders() {
            OrderExportService.OrderExportResult result = orderExportService.exportPendingOrders();

            assertThat(result.count()).isEqualTo(0);
            assertThat(result.xml()).contains("КоммерческаяИнформация");
            assertThat(result.xml()).doesNotContain("Документ");
        }

        @Test
        @DisplayName("не включает уже exported заказы")
        void shouldSkipExportedOrders() {
            PendingOrder exported = savePendingOrder("uuid-005", "ORD-00005");
            exported.setExported(true);
            exported.setExportedAt(LocalDateTime.now());
            pendingOrderRepository.save(exported);

            savePendingOrder("uuid-006", "ORD-00006");

            OrderExportService.OrderExportResult result = orderExportService.exportPendingOrders();

            assertThat(result.xml()).doesNotContain("uuid-005");
            assertThat(result.xml()).contains("uuid-006");
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
