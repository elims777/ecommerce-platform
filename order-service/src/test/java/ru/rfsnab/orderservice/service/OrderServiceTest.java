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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import ru.rfsnab.orderservice.BaseIntegrationTest;
import ru.rfsnab.orderservice.exception.CartEmptyException;
import ru.rfsnab.orderservice.exception.InsufficientStockException;
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
import ru.rfsnab.orderservice.repository.OrderRepository;
import ru.rfsnab.orderservice.repository.WarehousePointRepository;
import ru.rfsnab.orderservice.service.client.ProductServiceClient;

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
class OrderServiceIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private OrderService orderService;

    @Autowired
    private CartService cartService;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private WarehousePointRepository warehousePointRepository;

    @MockitoBean
    private ProductServiceClient productServiceClient;

    private static final Long USER_ID = 100L;
    private static final Long PRODUCT_ID_1 = 1L;
    private static final Long PRODUCT_ID_2 = 2L;

    private static final ProductDto PRODUCT_1 = new ProductDto(
            PRODUCT_ID_1, "Доска обрезная 50x150", new BigDecimal("1500.00"), 100, true);
    private static final ProductDto PRODUCT_2 = new ProductDto(
            PRODUCT_ID_2, "Брус 100x100", new BigDecimal("1000.00"), 50, true);

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
                    buildAddressDto(), null, "Тестовый заказ");

            Order order = orderService.createOrder(USER_ID, request);

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
        @DisplayName("создаёт заказ с самовывозом")
        void shouldCreatePickupOrder() {
            addItemsToCart();

            CreateOrderRequest request = new CreateOrderRequest(
                    PaymentMethod.CASH_ON_DELIVERY, DeliveryMethod.PICKUP,
                    null, savedWarehousePoint.getId(), null);

            Order order = orderService.createOrder(USER_ID, request);

            assertThat(order.getDeliveryMethod()).isEqualTo(DeliveryMethod.PICKUP);
            assertThat(order.getWarehousePointId()).isEqualTo(savedWarehousePoint.getId());
            assertThat(order.getDeliveryAddress()).isNull();
        }

        @Test
        @DisplayName("выбрасывает CartEmptyException при пустой корзине")
        void shouldThrowWhenCartEmpty() {
            CreateOrderRequest request = new CreateOrderRequest(
                    PaymentMethod.CARD, DeliveryMethod.PICKUP,
                    null, savedWarehousePoint.getId(), null);

            assertThatThrownBy(() -> orderService.createOrder(USER_ID, request))
                    .isInstanceOf(CartEmptyException.class);
        }

        @Test
        @DisplayName("выбрасывает InsufficientStockException при недостатке товара")
        void shouldThrowWhenInsufficientStock() {
            addItemsToCart();

            // Переопределяем мок — мало на складе
            when(productServiceClient.getProducts(anySet())).thenReturn(Map.of(
                    PRODUCT_ID_1, new ProductDto(PRODUCT_ID_1, "Доска", new BigDecimal("1500.00"), 1, true),
                    PRODUCT_ID_2, new ProductDto(PRODUCT_ID_2, "Брус", new BigDecimal("1000.00"), 100, true)
            ));

            CreateOrderRequest request = new CreateOrderRequest(
                    PaymentMethod.CARD, DeliveryMethod.PICKUP,
                    null, savedWarehousePoint.getId(), null);

            assertThatThrownBy(() -> orderService.createOrder(USER_ID, request))
                    .isInstanceOf(InsufficientStockException.class)
                    .hasMessageContaining("Доска");
        }

        @Test
        @DisplayName("выбрасывает исключение при PICKUP без warehousePointId")
        void shouldThrowWhenPickupWithoutWarehouse() {
            addItemsToCart();

            CreateOrderRequest request = new CreateOrderRequest(
                    PaymentMethod.CARD, DeliveryMethod.PICKUP,
                    null, null, null);

            assertThatThrownBy(() -> orderService.createOrder(USER_ID, request))
                    .isInstanceOf(InvalidOrderStateException.class)
                    .hasMessageContaining("точку получения");
        }

        @Test
        @DisplayName("выбрасывает исключение при DELIVERY без адреса")
        void shouldThrowWhenDeliveryWithoutAddress() {
            addItemsToCart();

            CreateOrderRequest request = new CreateOrderRequest(
                    PaymentMethod.CARD, DeliveryMethod.SUPPLIER_DELIVERY,
                    null, null, null);

            assertThatThrownBy(() -> orderService.createOrder(USER_ID, request))
                    .isInstanceOf(InvalidOrderStateException.class)
                    .hasMessageContaining("адрес");
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
                            new OrderItemDto(PRODUCT_ID_1, null, 20, null, null)))
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
            orderService.initiatePayment(order.getId(), USER_ID);

            UpdateOrderRequest request = UpdateOrderRequest.builder()
                    .paymentMethod(PaymentMethod.CARD)
                    .deliveryMethod(DeliveryMethod.PICKUP)
                    .warehousePointId(savedWarehousePoint.getId())
                    .items(List.of(new OrderItemDto(PRODUCT_ID_1, null, 5, null, null)))
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
                    .items(List.of(new OrderItemDto(PRODUCT_ID_1, null, 5, null, null)))
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

            Order repeated = orderService.repeatOrder(original.getId(), USER_ID);

            assertThat(repeated.getId()).isNotEqualTo(original.getId());
            assertThat(repeated.getOrderNumber()).isNotEqualTo(original.getOrderNumber());
            assertThat(repeated.getStatus()).isEqualTo(OrderStatus.CREATED);
            assertThat(repeated.getItems()).hasSize(original.getItems().size());
            assertThat(repeated.getPaymentMethod()).isEqualTo(original.getPaymentMethod());
            assertThat(repeated.getDeliveryMethod()).isEqualTo(original.getDeliveryMethod());
        }

        @Test
        @DisplayName("выбрасывает исключение при недостатке товара")
        void shouldThrowWhenInsufficientStockForRepeat() {
            Order original = createTestOrder();

            when(productServiceClient.getProducts(anySet())).thenReturn(Map.of(
                    PRODUCT_ID_1, new ProductDto(PRODUCT_ID_1, "Доска", new BigDecimal("1500.00"), 0, true),
                    PRODUCT_ID_2, new ProductDto(PRODUCT_ID_2, "Брус", new BigDecimal("1000.00"), 100, true)
            ));

            assertThatThrownBy(() -> orderService.repeatOrder(original.getId(), USER_ID))
                    .isInstanceOf(InsufficientStockException.class)
                    .hasMessageContaining("Доска");
        }
    }

    // ==================== Статусные переходы ====================

    @Nested
    @DisplayName("Статусные переходы")
    class StatusTransitionTests {

        @Test
        @DisplayName("полный happy path: CREATED → PENDING_PAYMENT → PAID → PROCESSING")
        void shouldFollowHappyPath() {
            Order order = createTestOrder();
            assertThat(order.getStatus()).isEqualTo(OrderStatus.CREATED);

            order = orderService.initiatePayment(order.getId(), USER_ID);
            assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);

            order = orderService.confirmPayment(order.getId());
            assertThat(order.getStatus()).isEqualTo(OrderStatus.PROCESSING);
        }

        @Test
        @DisplayName("PENDING_PAYMENT → PAYMENT_FAILED → PENDING_PAYMENT (повтор оплаты)")
        void shouldAllowRetryAfterPaymentFailure() {
            Order order = createTestOrder();
            orderService.initiatePayment(order.getId(), USER_ID);

            order = orderService.failPayment(order.getId());
            assertThat(order.getStatus()).isEqualTo(OrderStatus.PAYMENT_FAILED);

            order = orderService.initiatePayment(order.getId(), USER_ID);
            assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        }

        @Test
        @DisplayName("отмена в допустимых статусах")
        void shouldCancelInAllowedStatuses() {
            Order order = createTestOrder();
            order = orderService.cancelOrder(order.getId(), USER_ID);
            assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        }

        @Test
        @DisplayName("недопустимый переход: DELIVERED → CANCELLED")
        void shouldRejectInvalidTransition() {
            Order order = createTestOrder();

            orderService.initiatePayment(order.getId(), USER_ID);
            orderService.confirmPayment(order.getId());
            orderService.updateStatus(order.getId(), OrderStatus.SHIPPED);
            orderService.updateStatus(order.getId(), OrderStatus.IN_TRANSIT);
            orderService.updateStatus(order.getId(), OrderStatus.DELIVERED);

            UUID orderId = order.getId();
            assertThatThrownBy(() -> orderService.cancelOrder(orderId, USER_ID))
                    .isInstanceOf(InvalidOrderStateException.class);
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
                    null, savedWarehousePoint.getId(), null);

            Order order = orderService.createOrder(USER_ID, request);

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
        cartService.addItemToCart(USER_ID, PRODUCT_ID_1, 10);
        cartService.addItemToCart(USER_ID, PRODUCT_ID_2, 10);
    }

    private Order createTestOrder() {
        addItemsToCart();

        CreateOrderRequest request = new CreateOrderRequest(
                PaymentMethod.CARD, DeliveryMethod.SUPPLIER_DELIVERY,
                buildAddressDto(), null, "Тест");

        return orderService.createOrder(USER_ID, request);
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
}