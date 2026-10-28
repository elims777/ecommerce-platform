package ru.rfsnab.integrationservice.service.order;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.util.ReflectionTestUtils;
import ru.rfsnab.integrationservice.BaseIntegrationTest;
import ru.rfsnab.integrationservice.model.PendingOrder;
import ru.rfsnab.integrationservice.repository.PendingOrderRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

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
                            "sku": "ART-001",
                            "unitOfMeasure": "шт",
                            "quantity": 10,
                            "price": 250.50
                        },
                        {
                            "productId": 2,
                            "externalId": "ext-002",
                            "productName": "Каска строительная",
                            "sku": "ART-002",
                            "unitOfMeasure": "упак",
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

    private PendingOrder savePendingOrderWithCategory(String orderId, String orderNumber, String categoryExternalId) {
        String orderData = """
                {
                    "orderId": "%s",
                    "orderNumber": "%s",
                    "createdAt": "2026-03-20T10:00:00",
                    "userId": 100,
                    "customerEmail": "test@email.com",
                    "recipientName": "Иванов Иван",
                    "totalAmount": 15000.00,
                    "items": [
                        {
                            "productId": 1,
                            "externalId": "ext-001",
                            "productName": "Перчатки нитриловые L",
                            "sku": "ART-001",
                            "unitOfMeasure": "шт",
                            "quantity": 10,
                            "price": 250.50,
                            "categoryExternalId": %s
                        }
                    ]
                }
                """.formatted(orderId, orderNumber, categoryExternalId == null ? "null" : "\"" + categoryExternalId + "\"");

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

    private PendingOrder savePickupPendingOrder(String orderId, String orderNumber) {
        String orderData = """
                {
                    "orderId": "%s",
                    "orderNumber": "%s",
                    "createdAt": "2026-03-20T10:00:00",
                    "userId": 100,
                    "customerType": "B2C",
                    "customerEmail": "test@email.com",
                    "customerName": "Кокорин Максим",
                    "customerPhone": "+79998887771",
                    "recipientName": null,
                    "recipientPhone": null,
                    "deliveryMethod": "PICKUP",
                    "paymentMethod": "INVOICE",
                    "totalAmount": 15000.00,
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

    private PendingOrder saveOrderWithoutRecipientName(String orderId, String orderNumber) {
        String orderData = """
                {
                    "orderId": "%s",
                    "orderNumber": "%s",
                    "createdAt": "2026-03-20T10:00:00",
                    "userId": 100,
                    "customerType": "B2C",
                    "customerEmail": "test@email.com",
                    "recipientName": null,
                    "totalAmount": 15000.00,
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

    private PendingOrder saveOrderWithCustomerNameOnly(String orderId, String orderNumber) {
        String orderData = """
                {
                    "orderId": "%s",
                    "orderNumber": "%s",
                    "createdAt": "2026-03-20T10:00:00",
                    "userId": 100,
                    "customerType": "B2C",
                    "customerEmail": "test@email.com",
                    "recipientName": null,
                    "customerName": "Сидоров Сидор",
                    "totalAmount": 15000.00,
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
            assertThat(result.xml()).contains("КоммерческаяИнформация")
                    .contains("uuid-001")
                    .contains("uuid-002")
                    .contains("ORD-00001")
                    .contains("Перчатки нитриловые L")
                    .contains("ext-001");
        }

        @Test
        @DisplayName("товар: Артикул и БазоваяЕдиница с кодом ОКЕИ для сопоставления/создания в 1С")
        void shouldExportProductWithSkuAndBaseUnit() {
            savePendingOrder("uuid-prod", "ORD-PROD");

            OrderExportService.OrderExportResult result = orderExportService.exportPendingOrders();

            assertThat(result.xml()).contains("<Артикул>ART-001</Артикул>");
            assertThat(result.xml()).contains(
                    "<БазоваяЕдиница Код=\"796\" НаименованиеПолное=\"Штука\" МеждународноеСокращение=\"PCE\">шт</БазоваяЕдиница>");
            assertThat(result.xml()).contains(
                    "<БазоваяЕдиница Код=\"778\" НаименованиеПолное=\"Упаковка\" МеждународноеСокращение=\"NMP\">упак</БазоваяЕдиница>");
        }

        @Test
        @DisplayName("включает контрагента с ФИО")
        void shouldIncludeContragent() {
            savePendingOrder("uuid-003", "ORD-00003");

            OrderExportService.OrderExportResult result = orderExportService.exportPendingOrders();

            assertThat(result.xml()).contains("Контрагент")
                    .contains("<Наименование>Иванов Иван</Наименование>");
        }

        @Test
        @DisplayName("телефон — плоским тегом Телефон и дублем в Контакты, email — в Контакты (Тип Почта), адрес — в АдресРегистрации")
        void shouldIncludeContragentContacts() {
            savePendingOrder("uuid-addr", "ORD-ADDR");

            OrderExportService.OrderExportResult result = orderExportService.exportPendingOrders();

            assertThat(result.xml()).contains("<АдресРегистрации>")
                    .contains("<Телефон>+79001234567</Телефон>")
                    .contains("<Контакты>")
                    .contains("<Тип>Почта</Тип>")
                    .contains("<Значение>test@email.com</Значение>")
                    .contains("<Тип>Телефон</Тип>");
        }

        @Test
        @DisplayName("самовывоз без получателя: телефон контрагента = телефон заказчика (customerPhone)")
        void shouldFallbackToCustomerPhoneForPickup() {
            savePickupPendingOrder("uuid-pickup", "ORD-PICKUP");

            OrderExportService.OrderExportResult result = orderExportService.exportPendingOrders();

            assertThat(result.xml()).contains("<Телефон>+79998887771</Телефон>");
        }

        @Test
        @DisplayName("B2B заказ: контрагент — организация (плоские ОфициальноеНаименование+ИНН), без фамилии")
        void shouldIncludeCompanyContragentForB2B() {
            saveB2BPendingOrder("uuid-b2b-001", "ORD-B2B-001");

            OrderExportService.OrderExportResult result = orderExportService.exportPendingOrders();

            assertThat(result.xml()).contains("<Наименование>ООО Ромашка</Наименование>")
                    .contains("<ОфициальноеНаименование>ООО Ромашка</ОфициальноеНаименование>")
                    .contains("<ИНН>7701234567</ИНН>")
                    .doesNotContain("<Фамилия>");
        }

        @Test
        @DisplayName("B2C без получателя: Наименование = customerName (имя заказчика)")
        void shouldFallbackToCustomerNameWhenNoRecipientName() {
            saveOrderWithCustomerNameOnly("uuid-cust", "ORD-CUST");

            OrderExportService.OrderExportResult result = orderExportService.exportPendingOrders();

            assertThat(result.xml()).contains("<Наименование>Сидоров Сидор</Наименование>");
        }

        @Test
        @DisplayName("B2C без получателя и имени заказчика: Наименование = email (последний fallback)")
        void shouldFallbackToEmailWhenNoRecipientName() {
            saveOrderWithoutRecipientName("uuid-noname", "ORD-NONAME");

            OrderExportService.OrderExportResult result = orderExportService.exportPendingOrders();

            assertThat(result.xml()).contains("<Наименование>test@email.com</Наименование>");
        }

        @Test
        @DisplayName("Ид контрагента = userId (стабильный матчинг в 1С)")
        void shouldSetContragentIdFromUserId() {
            savePendingOrder("uuid-003", "ORD-00003");

            OrderExportService.OrderExportResult result = orderExportService.exportPendingOrders();

            assertThat(result.xml()).contains("<Ид>100</Ид>");
        }

        @Test
        @DisplayName("товар с categoryExternalId: содержит тег Группы с Ид категории")
        void shouldIncludeGroupsWhenCategoryExternalIdPresent() {
            savePendingOrderWithCategory("uuid-cat", "ORD-CAT", "cat-uuid-123");

            OrderExportService.OrderExportResult result = orderExportService.exportPendingOrders();

            assertThat(result.xml())
                    .contains("<Группы>")
                    .contains("<Ид>cat-uuid-123</Ид>");
        }

        @Test
        @DisplayName("товар без categoryExternalId: тег Группы отсутствует")
        void shouldNotIncludeGroupsWhenCategoryExternalIdMissing() {
            savePendingOrderWithCategory("uuid-nocat", "ORD-NOCAT", null);

            OrderExportService.OrderExportResult result = orderExportService.exportPendingOrders();

            assertThat(result.xml()).doesNotContain("Группы");
        }

        @Test
        @DisplayName("включает реквизиты: способ доставки и оплаты")
        void shouldIncludeRequisites() {
            savePendingOrder("uuid-004", "ORD-00004");

            OrderExportService.OrderExportResult result = orderExportService.exportPendingOrders();

            assertThat(result.xml()).contains("Способ доставки")
                    .contains("SUPPLIER_DELIVERY")
                    .contains("Способ оплаты")
                    .contains("CARD");
        }

        @Test
        @DisplayName("возвращает пустой XML если нет заказов")
        void shouldReturnEmptyXmlWhenNoOrders() {
            OrderExportService.OrderExportResult result = orderExportService.exportPendingOrders();

            assertThat(result.count()).isZero();
            assertThat(result.xml()).contains("КоммерческаяИнформация")
                    .doesNotContain("Документ");
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

            assertThat(result.xml()).doesNotContain("uuid-005")
                    .contains("uuid-006");
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

            orderExportService.exportPendingOrders();
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

            orderExportService.exportPendingOrders();
            orderExportService.markOrdersAsExported();

            PendingOrder reloaded = pendingOrderRepository.findAll().stream()
                    .filter(o -> "uuid-009".equals(o.getOrderId()))
                    .findFirst().orElseThrow();

            // exported_at не перезаписался
            assertThat(reloaded.getExportedAt()).isEqualTo(previousExportTime);
        }

        @Test
        @DisplayName("success без предшествующего query ничего не помечает")
        void shouldMarkNothingWithoutQuery() {
            ((AtomicReference<?>) ReflectionTestUtils.getField(orderExportService, "lastExportedSnapshot")).set(null);
            savePendingOrder("uuid-011", "ORD-00011");

            int count = orderExportService.markOrdersAsExported();

            assertThat(count).isZero();
            assertThat(pendingOrderRepository.findAll()).noneMatch(PendingOrder::isExported);
        }

        @Test
        @DisplayName("заказ, переотправленный после query (новый createdAt), остаётся непереданным")
        void shouldNotMarkOrderRequeuedAfterQuery() {
            PendingOrder requeued = savePendingOrder("uuid-012", "ORD-00012");

            orderExportService.exportPendingOrders();

            requeued.setCreatedAt(requeued.getCreatedAt().plusSeconds(1)); // upsert после query
            pendingOrderRepository.save(requeued);

            int count = orderExportService.markOrdersAsExported();

            assertThat(count).isZero();
            assertThat(pendingOrderRepository.findByOrderId("uuid-012").orElseThrow().isExported()).isFalse();
        }

        @Test
        @DisplayName("заказ, появившийся после query (даже с более ранним createdAt), не помечается")
        void shouldNotMarkOrderInsertedAfterQuery() {
            savePendingOrder("uuid-013", "ORD-00013");

            orderExportService.exportPendingOrders();

            PendingOrder late = savePendingOrder("uuid-014", "ORD-00014");
            late.setCreatedAt(LocalDateTime.now().minusMinutes(1));
            pendingOrderRepository.save(late);

            int count = orderExportService.markOrdersAsExported();

            assertThat(count).isEqualTo(1);
            assertThat(pendingOrderRepository.findByOrderId("uuid-013").orElseThrow().isExported()).isTrue();
            assertThat(pendingOrderRepository.findByOrderId("uuid-014").orElseThrow().isExported()).isFalse();
        }

        @Test
        @DisplayName("заказ с ошибкой формирования XML не помечается, остальные помечаются")
        void shouldNotMarkOrderWhoseDocumentFailed() {
            savePendingOrder("uuid-015", "ORD-00015");
            pendingOrderRepository.save(PendingOrder.builder()
                    .orderId("uuid-bad")
                    .orderData("{\"orderId\": \"uuid-bad\"}") // нет orderNumber → buildDocument падает
                    .externalId("ORD-BAD")
                    .build());

            OrderExportService.OrderExportResult result = orderExportService.exportPendingOrders();
            int count = orderExportService.markOrdersAsExported();

            assertThat(result.xml()).doesNotContain("uuid-bad");
            assertThat(count).isEqualTo(1);
            assertThat(pendingOrderRepository.findByOrderId("uuid-015").orElseThrow().isExported()).isTrue();
            assertThat(pendingOrderRepository.findByOrderId("uuid-bad").orElseThrow().isExported()).isFalse();
        }

        @Test
        @DisplayName("пустой query → success возвращает 0")
        void shouldReturnZeroAfterEmptyQuery() {
            orderExportService.exportPendingOrders();

            assertThat(orderExportService.markOrdersAsExported()).isZero();
        }
    }
}
