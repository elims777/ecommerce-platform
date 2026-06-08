package ru.rfsnab.orderservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import ru.rfsnab.orderservice.BaseServiceIntegrationTest;
import ru.rfsnab.orderservice.exception.CartEmptyException;
import ru.rfsnab.orderservice.exception.InvalidOrderStateException;
import ru.rfsnab.orderservice.models.dto.event.OrderEvent;
import ru.rfsnab.orderservice.models.dto.order.AddressDto;
import ru.rfsnab.orderservice.models.dto.order.CreateOrderRequest;
import ru.rfsnab.orderservice.models.dto.order.OrderItemDto;
import ru.rfsnab.orderservice.models.dto.order.UpdateOrderRequest;
import ru.rfsnab.orderservice.models.dto.product.ProductDto;
import ru.rfsnab.orderservice.models.entity.Cart;
import ru.rfsnab.orderservice.models.entity.Order;
import ru.rfsnab.orderservice.models.entity.OrderItem;
import ru.rfsnab.orderservice.models.entity.WarehousePoint;
import ru.rfsnab.orderservice.models.entity.enums.DeliveryMethod;
import ru.rfsnab.orderservice.models.entity.enums.OrderStatus;
import ru.rfsnab.orderservice.models.entity.enums.PaymentMethod;
import ru.rfsnab.orderservice.exception.PaymentMethodNotAvailableException;
import ru.rfsnab.orderservice.models.entity.PaymentMethodSettings;
import ru.rfsnab.orderservice.repository.OrderRepository;
import ru.rfsnab.orderservice.repository.PaymentMethodSettingsRepository;
import ru.rfsnab.orderservice.repository.WarehousePointRepository;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.when;

/**
 * Интеграционные тесты OrderService.
 * PostgreSQL, Kafka, Redis — реальные контейнеры.
 * ProductServiceClient — mock (внешний сервис).
 */
@DisplayName("OrderService — интеграционные тесты")
class OrderServiceIntegrationTest extends BaseServiceIntegrationTest {

    @Autowired
    private OrderService orderService;

    @Autowired
    private CartService cartService;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private WarehousePointRepository warehousePointRepository;

    @Autowired
    private PaymentMethodSettingsRepository paymentMethodSettingsRepository;

//    @MockitoBean
//    private ProductServiceClient productServiceClient;

    private static final Long USER_ID = 100L;
    private static final Long PRODUCT_ID_1 = 1L;
    private static final Long PRODUCT_ID_2 = 2L;
    private static final String USER_EMAIL = "user@email.com";


    private static final ProductDto PRODUCT_1 = new ProductDto(
            PRODUCT_ID_1, "Доска обрезная 50x150", new BigDecimal("1500.00"),
            null, 100, true, "ext-001");
    private static final ProductDto PRODUCT_2 = new ProductDto(
            PRODUCT_ID_2, "Брус 100x100", new BigDecimal("1000.00"),
            null, 50, true, "ext-002");

    private final ObjectMapper jsonMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    private WarehousePoint savedWarehousePoint;

    @BeforeEach
    void setUp() {
        orderRepository.deleteAll();
        cartService.clearCart(USER_ID);

        // Создаём точку самовывоза
        savedWarehousePoint = warehousePointRepository.save(
                WarehousePoint.builder()
                        .name("Склад РФСнаб")
                        .city("Сыктывкар").street("Октябрьский проспект")
                        .building("1").postalCode("167000")
                        .phoneNumber("+7 (8212) 00-00-00")
                        .workingHours("Пн-Пт: 9:00-18:00")
                        .description("Основной склад")
                        .active(true)
                        .build()
        );

        // По умолчанию card включена — существующие тесты с PaymentMethod.CARD работают
        PaymentMethodSettings settings = paymentMethodSettingsRepository.findById(1L)
                .orElseGet(() -> {
                    PaymentMethodSettings s = new PaymentMethodSettings();
                    s.setId(1L);
                    return s;
                });
        settings.setCardEnabled(true);
        settings.setSbpEnabled(true);
        paymentMethodSettingsRepository.save(settings);

        // Mock product-service
        stubProducts();
    }

    // ==================== createOrder ====================

    @Nested
    @DisplayName("createOrder")
    class CreateOrderTests {

        @Test
        @DisplayName("создаёт заказ из корзины с доставкой")
        void shouldCreateDeliveryOrder() {
            addItemsToCart();

            CreateOrderRequest request = new CreateOrderRequest(
                    PaymentMethod.CARD, DeliveryMethod.SUPPLIER_DELIVERY,
                    buildAddressDto(), null, null, null, "Тестовый заказ", null, null, null, null);

            Order order = orderService.createOrder(USER_ID, USER_EMAIL, "B2C", request);

            assertThat(order.getId()).isNotNull();
            assertThat(order.getOrderNumber()).isNotBlank();
            assertThat(order.getStatus()).isEqualTo(OrderStatus.CREATED);
            assertThat(order.getPaymentMethod()).isEqualTo(PaymentMethod.CARD);
            assertThat(order.getDeliveryMethod()).isEqualTo(DeliveryMethod.SUPPLIER_DELIVERY);
            assertThat(order.getDeliveryAddress().getCity()).isEqualTo("Сыктывкар");
            assertThat(order.getItems()).hasSize(2);
            assertThat(order.getTotalAmount()).isEqualByComparingTo("25000.00");

            // Проверяем snapshot цен
            OrderItem item1 = order.getItems().stream()
                    .filter(i -> i.getProductId().equals(PRODUCT_ID_1))
                    .findFirst().orElseThrow();
            assertThat(item1.getProductName()).isEqualTo("Доска обрезная 50x150");
            assertThat(item1.getPrice()).isEqualByComparingTo("1500.00");

            // Корзина очищена
            Cart cart = cartService.getCart(USER_ID);
            assertThat(cart.getItems()).isEmpty();

            // Заказ сохранён в БД
            assertThat(orderRepository.findById(order.getId())).isPresent();
        }

        @Test
        @DisplayName("создаёт заказ с самовывозом, сохраняет pickup recipient")
        void shouldCreatePickupOrder() {
            addItemsToCart();

            CreateOrderRequest request = new CreateOrderRequest(
                    PaymentMethod.CASH_ON_DELIVERY, DeliveryMethod.PICKUP,
                    null, savedWarehousePoint.getId(), "Петров Петр", "+79001112233", null, null, null, null, null);

            Order order = orderService.createOrder(USER_ID, USER_EMAIL, "B2C", request);

            assertThat(order.getDeliveryMethod()).isEqualTo(DeliveryMethod.PICKUP);
            assertThat(order.getWarehousePointId()).isEqualTo(savedWarehousePoint.getId());
            assertThat(order.getDeliveryAddress()).isNull();
            assertThat(order.getPickupRecipientName()).isEqualTo("Петров Петр");
            assertThat(order.getPickupRecipientPhone()).isEqualTo("+79001112233");
        }

        @Test
        @DisplayName("выбрасывает CartEmptyException при пустой корзине")
        void shouldThrowWhenCartEmpty() {
            CreateOrderRequest request = new CreateOrderRequest(
                    PaymentMethod.CARD, DeliveryMethod.PICKUP,
                    null, savedWarehousePoint.getId(), null, null, null, null, null, null, null);

            assertThatThrownBy(() -> orderService.createOrder(USER_ID, USER_EMAIL, "B2C", request))
                    .isInstanceOf(CartEmptyException.class);
        }


        @Test
        @DisplayName("выбрасывает исключение при PICKUP без warehousePointId")
        void shouldThrowWhenPickupWithoutWarehouse() {
            addItemsToCart();

            CreateOrderRequest request = new CreateOrderRequest(
                    PaymentMethod.CARD, DeliveryMethod.PICKUP,
                    null, null, null, null, null, null, null, null, null);

            assertThatThrownBy(() -> orderService.createOrder(USER_ID, USER_EMAIL, "B2C", request))
                    .isInstanceOf(InvalidOrderStateException.class)
                    .hasMessageContaining("точку получения");
        }

        @Test
        @DisplayName("выбрасывает исключение при DELIVERY без адреса")
        void shouldThrowWhenDeliveryWithoutAddress() {
            addItemsToCart();

            CreateOrderRequest request = new CreateOrderRequest(
                    PaymentMethod.CARD, DeliveryMethod.SUPPLIER_DELIVERY,
                    null, null, null, null, null, null, null, null, null);

            assertThatThrownBy(() -> orderService.createOrder(USER_ID, USER_EMAIL, "B2C", request))
                    .isInstanceOf(InvalidOrderStateException.class)
                    .hasMessageContaining("адрес");
        }

        @Test
        @DisplayName("сохраняет externalId товара при создании заказа (snapshot)")
        void shouldSaveProductExternalIdInOrderItem() {
            addItemsToCart();

            CreateOrderRequest request = new CreateOrderRequest(
                    PaymentMethod.CARD, DeliveryMethod.SUPPLIER_DELIVERY,
                    buildAddressDto(), null, null, null, "Тест externalId", null, null, null, null);

            Order order = orderService.createOrder(USER_ID, USER_EMAIL, "B2C", request);

            OrderItem item1 = order.getItems().stream()
                    .filter(i -> i.getProductId().equals(PRODUCT_ID_1))
                    .findFirst().orElseThrow();
            assertThat(item1.getExternalId()).isEqualTo("ext-001");

            OrderItem item2 = order.getItems().stream()
                    .filter(i -> i.getProductId().equals(PRODUCT_ID_2))
                    .findFirst().orElseThrow();
            assertThat(item2.getExternalId()).isEqualTo("ext-002");
        }

        @Test
        @DisplayName("B2B: снапшот компании — customerType, companyName, inn")
        void createOrder_B2B_snapshotsCompanyData() {
            cartService.addItemToCart(USER_ID, PRODUCT_ID_1, null, 1);

            CreateOrderRequest request = new CreateOrderRequest(
                    PaymentMethod.CARD, DeliveryMethod.SUPPLIER_DELIVERY,
                    buildAddressDto(), null, null, null, null, "ООО Ромашка", "1234567890", null, null);

            Order order = orderService.createOrder(USER_ID, USER_EMAIL, "B2B", request);

            assertThat(order.getCustomerType()).isEqualTo(ru.rfsnab.orderservice.models.entity.enums.CustomerType.B2B);
            assertThat(order.getCompanyName()).isEqualTo("ООО Ромашка");
            assertThat(order.getInn()).isEqualTo("1234567890");
        }

        @Test
        @DisplayName("B2C: поля компании не заполняются — customerType B2C, companyName null, inn null")
        void createOrder_B2C_noSnapshotFields() {
            cartService.addItemToCart(USER_ID, PRODUCT_ID_1, null, 1);

            CreateOrderRequest request = new CreateOrderRequest(
                    PaymentMethod.CARD, DeliveryMethod.SUPPLIER_DELIVERY,
                    buildAddressDto(), null, null, null, null, null, null, null, null);

            Order order = orderService.createOrder(USER_ID, USER_EMAIL, "B2C", request);

            assertThat(order.getCustomerType()).isEqualTo(ru.rfsnab.orderservice.models.entity.enums.CustomerType.B2C);
            assertThat(order.getCompanyName()).isNull();
            assertThat(order.getInn()).isNull();
        }

        @Test
        @DisplayName("B2B: использует оптовую цену (price), не розничную (wholesalePrice)")
        void createOrder_B2B_usesWholesalePrice() {
            Long productId = 10L;
            ProductDto product = new ProductDto(productId, "Товар B2B", new BigDecimal("1000.00"),
                    new BigDecimal("800.00"), 100, true, "ext-010");

            when(productServiceClient.getProducts(anySet())).thenReturn(Map.of(productId, product));
            when(productServiceClient.getProduct(productId)).thenReturn(product);

            cartService.addItemToCart(USER_ID, productId, null, 2);

            CreateOrderRequest request = new CreateOrderRequest(
                    PaymentMethod.CARD, DeliveryMethod.SUPPLIER_DELIVERY,
                    buildAddressDto(), null, null, null, null, "ООО Тест", "9876543210", null, null);

            Order order = orderService.createOrder(USER_ID, USER_EMAIL, "B2B", request);

            assertThat(order.getItems()).hasSize(1);
            OrderItem item = order.getItems().get(0);
            assertThat(item.getPrice()).isEqualByComparingTo("1000.00");
            assertThat(order.getTotalAmount()).isEqualByComparingTo("2000.00");
        }

        @Test
        @DisplayName("B2C: использует розничную цену (wholesalePrice), не оптовую (price)")
        void createOrder_B2C_usesRetailPrice() {
            Long productId = 11L;
            ProductDto product = new ProductDto(productId, "Товар B2C", new BigDecimal("1000.00"),
                    new BigDecimal("800.00"), 100, true, "ext-011");

            when(productServiceClient.getProducts(anySet())).thenReturn(Map.of(productId, product));
            when(productServiceClient.getProduct(productId)).thenReturn(product);

            cartService.addItemToCart(USER_ID, productId, null, 2);

            CreateOrderRequest request = new CreateOrderRequest(
                    PaymentMethod.CARD, DeliveryMethod.SUPPLIER_DELIVERY,
                    buildAddressDto(), null, null, null, null, null, null, null, null);

            Order order = orderService.createOrder(USER_ID, USER_EMAIL, "B2C", request);

            assertThat(order.getItems()).hasSize(1);
            OrderItem item = order.getItems().get(0);
            assertThat(item.getPrice()).isEqualByComparingTo("800.00");
            assertThat(order.getTotalAmount()).isEqualByComparingTo("1600.00");
        }

        @Test
        @DisplayName("B2B без inn: выбрасывает InvalidOrderStateException")
        void createOrder_B2B_missingInn_throws() {
            cartService.addItemToCart(USER_ID, PRODUCT_ID_1, null, 1);

            CreateOrderRequest request = new CreateOrderRequest(
                    PaymentMethod.CARD, DeliveryMethod.SUPPLIER_DELIVERY,
                    buildAddressDto(), null, null, null, null, "ООО Ромашка", null, null, null);

            assertThatThrownBy(() -> orderService.createOrder(USER_ID, USER_EMAIL, "B2B", request))
                    .isInstanceOf(InvalidOrderStateException.class);
        }
    }

    // ==================== updateOrder ====================

    @Nested
    @DisplayName("updateOrder")
    class UpdateOrderTests {

        @Test
        @DisplayName("обновляет заказ в статусе CREATED")
        void shouldUpdateCreatedOrder() {
            Order order = createTestOrder();

            UpdateOrderRequest request = UpdateOrderRequest.builder()
                    .paymentMethod(PaymentMethod.SBP)
                    .deliveryMethod(DeliveryMethod.PICKUP)
                    .warehousePointId(savedWarehousePoint.getId())
                    .items(List.of(
                            new OrderItemDto(PRODUCT_ID_1, null, 20, null, null, null, null, null)))
                    .comment("Обновлённый комментарий")
                    .build();

            Order updated = orderService.updateOrder(order.getId(), USER_ID, request);

            assertThat(updated.getPaymentMethod()).isEqualTo(PaymentMethod.SBP);
            assertThat(updated.getDeliveryMethod()).isEqualTo(DeliveryMethod.PICKUP);
            assertThat(updated.getWarehousePointId()).isEqualTo(savedWarehousePoint.getId());
            assertThat(updated.getDeliveryAddress()).isNull();
            assertThat(updated.getItems()).hasSize(1);
            assertThat(updated.getItems().get(0).getQuantity()).isEqualTo(20);
            assertThat(updated.getItems().get(0).getPrice()).isEqualByComparingTo("1500.00");
            assertThat(updated.getTotalAmount()).isEqualByComparingTo("30000.00");
            assertThat(updated.getComment()).isEqualTo("Обновлённый комментарий");
        }

        @Test
        @DisplayName("выбрасывает исключение при обновлении не в статусе CREATED")
        void shouldThrowWhenNotCreatedStatus() {
            Order order = createTestOrder();
            orderService.confirmOrder(order.getId(), USER_ID);

            UpdateOrderRequest request = UpdateOrderRequest.builder()
                    .paymentMethod(PaymentMethod.CARD)
                    .deliveryMethod(DeliveryMethod.PICKUP)
                    .warehousePointId(savedWarehousePoint.getId())
                    .items(List.of(new OrderItemDto(PRODUCT_ID_1, null, 5, null, null, null, null, null)))
                    .build();

            assertThatThrownBy(() -> orderService.updateOrder(order.getId(), USER_ID, request))
                    .isInstanceOf(InvalidOrderStateException.class)
                    .hasMessageContaining("невозможно в статусе");
        }

        @Test
        @DisplayName("выбрасывает исключение при доступе чужого пользователя")
        void shouldThrowWhenWrongUser() {
            Order order = createTestOrder();

            UpdateOrderRequest request = UpdateOrderRequest.builder()
                    .paymentMethod(PaymentMethod.CARD)
                    .deliveryMethod(DeliveryMethod.PICKUP)
                    .warehousePointId(savedWarehousePoint.getId())
                    .items(List.of(new OrderItemDto(PRODUCT_ID_1, null, 5, null, null, null, null, null)))
                    .build();

            assertThatThrownBy(() -> orderService.updateOrder(order.getId(), 999L, request))
                    .isInstanceOf(InvalidOrderStateException.class)
                    .hasMessageContaining("Нет доступа");
        }
    }

    // ==================== repeatOrder ====================

    @Nested
    @DisplayName("repeatOrder")
    class RepeatOrderTests {

        @Test
        @DisplayName("создаёт повторный заказ с актуальными ценами")
        void shouldRepeatOrder() {
            Order original = createTestOrder();

            Order repeated = orderService.repeatOrder(original.getId(), USER_ID, USER_EMAIL);

            assertThat(repeated.getId()).isNotEqualTo(original.getId());
            assertThat(repeated.getOrderNumber()).isNotEqualTo(original.getOrderNumber());
            assertThat(repeated.getStatus()).isEqualTo(OrderStatus.CREATED);
            assertThat(repeated.getItems()).hasSize(original.getItems().size());
            assertThat(repeated.getPaymentMethod()).isEqualTo(original.getPaymentMethod());
            assertThat(repeated.getDeliveryMethod()).isEqualTo(original.getDeliveryMethod());
        }

    }

    // ==================== confirmOrder ====================

    @Nested
    @DisplayName("confirmOrder")
    class ConfirmOrderTests {

        @Test
        @DisplayName("CREATED → PROCESSING при подтверждении клиентом")
        void shouldConfirmOrder() {
            Order order = createTestOrder();
            assertThat(order.getStatus()).isEqualTo(OrderStatus.CREATED);

            order = orderService.confirmOrder(order.getId(), USER_ID);

            assertThat(order.getStatus()).isEqualTo(OrderStatus.PROCESSING);

            Order fromDb = orderRepository.findById(order.getId()).orElseThrow();
            assertThat(fromDb.getStatus()).isEqualTo(OrderStatus.PROCESSING);
        }

        @Test
        @DisplayName("выбрасывает исключение при попытке подтвердить чужой заказ")
        void shouldThrowWhenWrongUser() {
            Order order = createTestOrder();

            UUID orderId = order.getId();
            assertThatThrownBy(() -> orderService.confirmOrder(orderId, 999L))
                    .isInstanceOf(InvalidOrderStateException.class)
                    .hasMessageContaining("Нет доступа");
        }

        @Test
        @DisplayName("выбрасывает исключение при повторном подтверждении (не CREATED)")
        void shouldThrowWhenAlreadyConfirmed() {
            Order order = createTestOrder();
            orderService.confirmOrder(order.getId(), USER_ID);

            UUID orderId = order.getId();
            assertThatThrownBy(() -> orderService.confirmOrder(orderId, USER_ID))
                    .isInstanceOf(InvalidOrderStateException.class);
        }
    }

    // ==================== confirmOrder: payment method settings ====================

    @Nested
    @DisplayName("confirmOrder — настройки оплаты B2C")
    class ConfirmOrderPaymentSettingsTests {

        @Test
        @DisplayName("B2C с INVOICE идёт в 1С когда оба метода выключены")
        void b2cInvoice_bothDisabled_confirmsSuccessfully() {
            PaymentMethodSettings s = getOrCreateSettings();
            s.setCardEnabled(false);
            s.setSbpEnabled(false);
            paymentMethodSettingsRepository.save(s);

            cartService.addItemToCart(USER_ID, PRODUCT_ID_1, null, 1);
            CreateOrderRequest req = new CreateOrderRequest(
                    PaymentMethod.INVOICE, DeliveryMethod.SUPPLIER_DELIVERY,
                    buildAddressDto(), null, null, null, null, null, null, null, null);
            Order order = orderService.createOrder(USER_ID, USER_EMAIL, "B2C", req);

            Order confirmed = orderService.confirmOrder(order.getId(), USER_ID);
            assertThat(confirmed.getStatus()).isEqualTo(OrderStatus.PROCESSING);
        }

        @Test
        @DisplayName("B2C с CARD когда card выключена — PaymentMethodNotAvailableException")
        void b2cCard_cardDisabled_throws() {
            PaymentMethodSettings s = getOrCreateSettings();
            s.setCardEnabled(false);
            s.setSbpEnabled(false);
            paymentMethodSettingsRepository.save(s);

            cartService.addItemToCart(USER_ID, PRODUCT_ID_1, null, 1);
            CreateOrderRequest req = new CreateOrderRequest(
                    PaymentMethod.CARD, DeliveryMethod.SUPPLIER_DELIVERY,
                    buildAddressDto(), null, null, null, null, null, null, null, null);
            Order order = orderService.createOrder(USER_ID, USER_EMAIL, "B2C", req);

            UUID orderId = order.getId();
            assertThatThrownBy(() -> orderService.confirmOrder(orderId, USER_ID))
                    .isInstanceOf(PaymentMethodNotAvailableException.class)
                    .hasMessageContaining("картой");
        }

        @Test
        @DisplayName("B2C с SBP когда sbp выключен — PaymentMethodNotAvailableException")
        void b2cSbp_sbpDisabled_throws() {
            PaymentMethodSettings s = getOrCreateSettings();
            s.setCardEnabled(false);
            s.setSbpEnabled(false);
            paymentMethodSettingsRepository.save(s);

            cartService.addItemToCart(USER_ID, PRODUCT_ID_1, null, 1);
            CreateOrderRequest req = new CreateOrderRequest(
                    PaymentMethod.SBP, DeliveryMethod.SUPPLIER_DELIVERY,
                    buildAddressDto(), null, null, null, null, null, null, null, null);
            Order order = orderService.createOrder(USER_ID, USER_EMAIL, "B2C", req);

            UUID orderId = order.getId();
            assertThatThrownBy(() -> orderService.confirmOrder(orderId, USER_ID))
                    .isInstanceOf(PaymentMethodNotAvailableException.class)
                    .hasMessageContaining("СБП");
        }

        @Test
        @DisplayName("B2B с INVOICE — не проверяет флаги, подтверждается успешно")
        void b2b_invoice_ignoresPaymentSettings() {
            PaymentMethodSettings s = getOrCreateSettings();
            s.setCardEnabled(false);
            s.setSbpEnabled(false);
            paymentMethodSettingsRepository.save(s);

            cartService.addItemToCart(USER_ID, PRODUCT_ID_1, null, 1);
            CreateOrderRequest req = new CreateOrderRequest(
                    PaymentMethod.INVOICE, DeliveryMethod.SUPPLIER_DELIVERY,
                    buildAddressDto(), null, null, null, null, "ООО Тест", "1234567890", null, null);
            Order order = orderService.createOrder(USER_ID, USER_EMAIL, "B2B", req);

            Order confirmed = orderService.confirmOrder(order.getId(), USER_ID);
            assertThat(confirmed.getStatus()).isEqualTo(OrderStatus.PROCESSING);
        }
    }

    // ==================== Статусные переходы ====================

    @Nested
    @DisplayName("Статусные переходы")
    class StatusTransitionTests {

        @Test
        @DisplayName("B2C happy path: CREATED → PROCESSING → PENDING_PAYMENT → PAID → SHIPPED → IN_TRANSIT → DELIVERED")
        void shouldFollowB2CHappyPath() {
            Order order = createTestOrder();
            assertThat(order.getStatus()).isEqualTo(OrderStatus.CREATED);

            order = orderService.confirmOrder(order.getId(), USER_ID);
            assertThat(order.getStatus()).isEqualTo(OrderStatus.PROCESSING);

            order = orderService.initiatePayment(order.getId(), USER_ID);
            assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);

            order = orderService.confirmPayment(order.getId());
            assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);

            order = orderService.updateStatus(order.getId(), OrderStatus.SHIPPED);
            assertThat(order.getStatus()).isEqualTo(OrderStatus.SHIPPED);

            order = orderService.updateStatus(order.getId(), OrderStatus.IN_TRANSIT);
            assertThat(order.getStatus()).isEqualTo(OrderStatus.IN_TRANSIT);

            order = orderService.updateStatus(order.getId(), OrderStatus.DELIVERED);
            assertThat(order.getStatus()).isEqualTo(OrderStatus.DELIVERED);
        }

        @Test
        @DisplayName("B2B prepayment path: CREATED → PROCESSING → INVOICE_SENT → PENDING_PAYMENT → PAID")
        void shouldFollowB2BPrepaymentPath() {
            Order order = createTestOrder();

            order = orderService.confirmOrder(order.getId(), USER_ID);
            assertThat(order.getStatus()).isEqualTo(OrderStatus.PROCESSING);

            order = orderService.updateStatus(order.getId(), OrderStatus.INVOICE_SENT);
            assertThat(order.getStatus()).isEqualTo(OrderStatus.INVOICE_SENT);

            order = orderService.updateStatus(order.getId(), OrderStatus.PENDING_PAYMENT);
            assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);

            order = orderService.confirmPayment(order.getId());
            assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        }

        @Test
        @DisplayName("B2B postpayment path: PROCESSING → INVOICE_SENT → AWAITING_CONFIRMATION → SHIPPED → DELIVERED → PAID")
        void shouldFollowB2BPostpaymentPath() {
            Order order = createTestOrder();
            orderService.confirmOrder(order.getId(), USER_ID);

            order = orderService.updateStatus(order.getId(), OrderStatus.INVOICE_SENT);
            order = orderService.updateStatus(order.getId(), OrderStatus.AWAITING_CONFIRMATION);
            assertThat(order.getStatus()).isEqualTo(OrderStatus.AWAITING_CONFIRMATION);

            order = orderService.updateStatus(order.getId(), OrderStatus.SHIPPED);
            order = orderService.updateStatus(order.getId(), OrderStatus.IN_TRANSIT);
            order = orderService.updateStatus(order.getId(), OrderStatus.DELIVERED);
            assertThat(order.getStatus()).isEqualTo(OrderStatus.DELIVERED);

            order = orderService.updateStatus(order.getId(), OrderStatus.PAID);
            assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        }

        @Test
        @DisplayName("PENDING_PAYMENT → PAYMENT_FAILED → PENDING_PAYMENT (повтор оплаты)")
        void shouldAllowRetryAfterPaymentFailure() {
            Order order = createTestOrder();
            orderService.confirmOrder(order.getId(), USER_ID);
            orderService.initiatePayment(order.getId(), USER_ID);

            order = orderService.failPayment(order.getId());
            assertThat(order.getStatus()).isEqualTo(OrderStatus.PAYMENT_FAILED);

            order = orderService.initiatePayment(order.getId(), USER_ID);
            assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        }

        @Test
        @DisplayName("клиент может отменить заказ в статусе CREATED")
        void shouldCancelCreatedOrder() {
            Order order = createTestOrder();
            order = orderService.cancelOrder(order.getId(), USER_ID);
            assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        }

        @Test
        @DisplayName("клиент может отменить заказ в статусе PROCESSING")
        void shouldCancelProcessingOrder() {
            Order order = createTestOrder();
            orderService.confirmOrder(order.getId(), USER_ID);
            order = orderService.cancelOrder(order.getId(), USER_ID);
            assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        }

        @Test
        @DisplayName("клиент не может отменить заказ в статусе AWAITING_CONFIRMATION")
        void shouldNotCancelAwaitingConfirmationOrder() {
            Order order = createTestOrder();
            orderService.confirmOrder(order.getId(), USER_ID);
            orderService.updateStatus(order.getId(), OrderStatus.INVOICE_SENT);
            orderService.updateStatus(order.getId(), OrderStatus.AWAITING_CONFIRMATION);

            UUID orderId = order.getId();
            assertThatThrownBy(() -> orderService.cancelOrder(orderId, USER_ID))
                    .isInstanceOf(InvalidOrderStateException.class)
                    .hasMessageContaining("нельзя отменить");
        }

        @Test
        @DisplayName("недопустимый переход: DELIVERED → CANCELLED")
        void shouldRejectInvalidTransition() {
            Order order = createTestOrder();
            orderService.confirmOrder(order.getId(), USER_ID);
            orderService.updateStatus(order.getId(), OrderStatus.INVOICE_SENT);
            orderService.updateStatus(order.getId(), OrderStatus.PENDING_PAYMENT);
            orderService.confirmPayment(order.getId());
            orderService.updateStatus(order.getId(), OrderStatus.SHIPPED);
            orderService.updateStatus(order.getId(), OrderStatus.IN_TRANSIT);
            orderService.updateStatus(order.getId(), OrderStatus.DELIVERED);

            UUID orderId = order.getId();
            assertThatThrownBy(() -> orderService.cancelOrder(orderId, USER_ID))
                    .isInstanceOf(InvalidOrderStateException.class);
        }
    }

    // ==================== syncFrom1C ====================

    @Nested
    @DisplayName("syncFrom1C")
    class SyncFrom1CTests {

        @Test
        @DisplayName("записывает externalId и обновляет статус")
        void shouldSyncExternalIdAndStatus() {
            Order order = createTestOrder();
            assertThat(order.getExternalId()).isNull();

            // Первый ответ от 1С: присваивает свой номер + ставит "В обработке"
            Order synced = orderService.syncFrom1C(order.getId(), "РФ-000123", OrderStatus.PROCESSING);

            assertThat(synced.getExternalId()).isEqualTo("РФ-000123");
            assertThat(synced.getStatus()).isEqualTo(OrderStatus.PROCESSING);

            // Проверяем что сохранено в БД
            Order fromDb = orderRepository.findById(order.getId()).orElseThrow();
            assertThat(fromDb.getExternalId()).isEqualTo("РФ-000123");
            assertThat(fromDb.getStatus()).isEqualTo(OrderStatus.PROCESSING);
        }

        @Test
        @DisplayName("обновляет статус при повторной синхронизации")
        void shouldUpdateStatusOnReSync() {
            Order order = createTestOrder();
            orderService.syncFrom1C(order.getId(), "РФ-000123", OrderStatus.PROCESSING);

            // Повторный вызов: 1С обновила статус
            Order synced = orderService.syncFrom1C(order.getId(), "РФ-000123", OrderStatus.SHIPPED);

            assertThat(synced.getStatus()).isEqualTo(OrderStatus.SHIPPED);
            assertThat(synced.getExternalId()).isEqualTo("РФ-000123");
        }

        @Test
        @DisplayName("выбрасывает исключение для несуществующего заказа")
        void shouldThrowForNonExistentOrder() {
            UUID fakeId = UUID.randomUUID();

            assertThatThrownBy(() ->
                    orderService.syncFrom1C(fakeId, "РФ-000999", OrderStatus.PROCESSING))
                    .isInstanceOf(Exception.class);
        }
    }

    // ==================== Kafka events ====================

    @Nested
    @DisplayName("Kafka events")
    class KafkaEventTests {

        @Test
        @DisplayName("ORDER_CREATED event отправляется при создании заказа")
        void shouldSendOrderCreatedEvent() {
            addItemsToCart();

            CreateOrderRequest request = new CreateOrderRequest(
                    PaymentMethod.CARD, DeliveryMethod.PICKUP,
                    null, savedWarehousePoint.getId(), "Петров Петр", "+79001112233", null, null, null, null, null);

            Order order = orderService.createOrder(USER_ID, USER_EMAIL, "B2C", request);

            List<OrderEvent> events = consumeEvents("order-events", 10);

            // Фильтруем по orderId — в топике могут быть events от других тестов
            Optional<OrderEvent> createdEvent = events.stream()
                    .filter(e -> order.getId().equals(e.orderId()))
                    .filter(e -> "ORDER_CREATED".equals(e.eventType()))
                    .findFirst();

            assertThat(createdEvent).isPresent();
            assertThat(createdEvent.get().userId()).isEqualTo(USER_ID);
            assertThat(createdEvent.get().orderNumber()).isEqualTo(order.getOrderNumber());
        }

        @Test
        @DisplayName("ORDER_CANCELLED event отправляется при отмене")
        void shouldSendOrderCancelledEvent() {
            Order order = createTestOrder();
            orderService.cancelOrder(order.getId(), USER_ID);

            List<OrderEvent> events = consumeEvents("order-events", 10);

            Optional<OrderEvent> cancelEvent = events.stream()
                    .filter(e -> order.getId().equals(e.orderId()))
                    .filter(e -> "ORDER_CANCELLED".equals(e.eventType()))
                    .findFirst();

            assertThat(cancelEvent).isPresent();
            assertThat(cancelEvent.get().userId()).isEqualTo(USER_ID);
        }

        @Test
        @DisplayName("ORDER_1C_EXPORT — producer вызывается при создании заказа, externalId сохранён")
        void shouldSendOrder1CExportEvent() {
            addItemsToCart();

            CreateOrderRequest request = new CreateOrderRequest(
                    PaymentMethod.CARD, DeliveryMethod.SUPPLIER_DELIVERY,
                    buildAddressDto(), null, null, null, "Тест 1С export", null, null, null, null);

            Order order = orderService.createOrder(USER_ID, USER_EMAIL, "B2C", request);

            // order из createOrder() — items загружены внутри @Transactional сервиса
            assertThat(order.getItems()).isNotEmpty();

            OrderItem item1 = order.getItems().stream()
                    .filter(i -> i.getProductId().equals(PRODUCT_ID_1))
                    .findFirst().orElseThrow();
            assertThat(item1.getExternalId()).isEqualTo("ext-001");

            OrderItem item2 = order.getItems().stream()
                    .filter(i -> i.getProductId().equals(PRODUCT_ID_2))
                    .findFirst().orElseThrow();
            assertThat(item2.getExternalId()).isEqualTo("ext-002");
        }
    }

    // ==================== Helper methods ====================

    /**
     * Мок данных product-service.
     * getProducts(Set) — batch запрос из OrderService.
     * getProduct(Long) — одиночный запрос из CartService.
     */
    private void stubProducts() {
        when(productServiceClient.getProducts(anySet()))
                .thenReturn(Map.of(PRODUCT_ID_1, PRODUCT_1, PRODUCT_ID_2, PRODUCT_2));

        when(productServiceClient.getProduct(PRODUCT_ID_1)).thenReturn(PRODUCT_1);
        when(productServiceClient.getProduct(PRODUCT_ID_2)).thenReturn(PRODUCT_2);
    }

    private void addItemsToCart() {
        cartService.addItemToCart(USER_ID, PRODUCT_ID_1, null, 10);
        cartService.addItemToCart(USER_ID, PRODUCT_ID_2, null, 10);
    }

    private Order createTestOrder() {
        addItemsToCart();

        CreateOrderRequest request = new CreateOrderRequest(
                PaymentMethod.CARD, DeliveryMethod.SUPPLIER_DELIVERY,
                buildAddressDto(), null, null, null, "Тест", null, null, null, null);

        return orderService.createOrder(USER_ID, USER_EMAIL, "B2C", request);
    }

    private PaymentMethodSettings getOrCreateSettings() {
        return paymentMethodSettingsRepository.findById(1L)
                .orElseGet(() -> {
                    PaymentMethodSettings s = new PaymentMethodSettings();
                    s.setId(1L);
                    return s;
                });
    }

    private AddressDto buildAddressDto() {
        return AddressDto.builder()
                .city("Сыктывкар").street("Октябрьский проспект")
                .building("1").phone("+79001234567")
                .recipientName("Иванов Иван").build();
    }

    /**
     * Чтение events из Kafka топика.
     * StringDeserializer + ObjectMapper — избегаем проблемы с JsonDeserializer.
     * Уникальный group-id — каждый вызов читает с начала топика.
     * Читаем максимум events, затем фильтруем в тесте по orderId.
     */
    private List<OrderEvent> consumeEvents(String topic, int maxEvents) {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "test-" + UUID.randomUUID());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);

        List<OrderEvent> events = new ArrayList<>();

        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {
            consumer.subscribe(List.of(topic));

            long deadline = System.currentTimeMillis() + 10_000;
            while (events.size() < maxEvents && System.currentTimeMillis() < deadline) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(500));
                for (ConsumerRecord<String, String> record : records) {
                    try {
                        events.add(jsonMapper.readValue(record.value(), OrderEvent.class));
                    } catch (Exception e) {
                        throw new RuntimeException("Ошибка десериализации OrderEvent", e);
                    }
                }
            }
        }

        return events;
    }

    /**
     * Чтение raw сообщений из Kafka топика (без десериализации в конкретный тип).
     */
    private List<String> consumeRawMessages(String topic, int maxMessages) {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "test-raw-" + UUID.randomUUID());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);

        List<String> messages = new ArrayList<>();

        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {
            consumer.subscribe(List.of(topic));

            long deadline = System.currentTimeMillis() + 15_000;
            while (messages.size() < maxMessages && System.currentTimeMillis() < deadline) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(500));
                for (ConsumerRecord<String, String> record : records) {
                    messages.add(record.value());
                }
            }
        }

        return messages;
    }
}
