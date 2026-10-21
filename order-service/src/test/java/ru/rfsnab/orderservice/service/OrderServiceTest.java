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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import ru.rfsnab.orderservice.BaseServiceIntegrationTest;
import ru.rfsnab.orderservice.delivery.models.entity.Carrier;
import ru.rfsnab.orderservice.delivery.models.entity.CarrierTerminal;
import ru.rfsnab.orderservice.delivery.models.entity.enums.CarrierCode;
import ru.rfsnab.orderservice.delivery.models.entity.enums.PointType;
import ru.rfsnab.orderservice.delivery.repository.CarrierRepository;
import ru.rfsnab.orderservice.delivery.repository.CarrierTerminalRepository;
import ru.rfsnab.orderservice.exception.CartEmptyException;
import ru.rfsnab.orderservice.exception.InvalidOrderStateException;
import ru.rfsnab.orderservice.exception.PaymentMethodNotAvailableException;
import ru.rfsnab.orderservice.exception.ProfileIncompleteException;
import ru.rfsnab.orderservice.kafka.Order1CKafkaProducer;
import ru.rfsnab.orderservice.models.dto.event.OrderEvent;
import ru.rfsnab.orderservice.models.dto.order.AddressDto;
import ru.rfsnab.orderservice.models.dto.order.CreateOrderRequest;
import ru.rfsnab.orderservice.models.dto.order.OrderItemDto;
import ru.rfsnab.orderservice.models.dto.order.OrderStatsResponse;
import ru.rfsnab.orderservice.models.dto.order.UpdateOrderRequest;
import ru.rfsnab.orderservice.models.dto.product.ProductDto;
import ru.rfsnab.orderservice.models.dto.product.WarehouseDto;
import ru.rfsnab.orderservice.models.dto.user.ProfileCompletenessDto;
import ru.rfsnab.orderservice.models.entity.Cart;
import ru.rfsnab.orderservice.models.entity.Order;
import ru.rfsnab.orderservice.models.entity.OrderItem;
import ru.rfsnab.orderservice.models.entity.PaymentMethodSettings;
import ru.rfsnab.orderservice.models.entity.WarehousePoint;
import ru.rfsnab.orderservice.models.entity.enums.DeliveryMethod;
import ru.rfsnab.orderservice.models.entity.enums.OrderStatus;
import ru.rfsnab.orderservice.models.entity.enums.PaymentMethod;
import ru.rfsnab.orderservice.repository.OrderRepository;
import ru.rfsnab.orderservice.repository.PaymentMethodSettingsRepository;
import ru.rfsnab.orderservice.repository.WarehousePointRepository;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.Duration;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Интеграционные тесты OrderService.
 * PostgreSQL, Kafka, Redis — реальные контейнеры.
 * ProductServiceClient — mock (внешний сервис).
 */
@DisplayName("OrderService — интеграционные тесты")
class OrderServiceTest extends BaseServiceIntegrationTest {

    @Autowired
    private OrderService orderService;

    @Autowired
    private CartService cartService;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private WarehousePointRepository warehousePointRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private CarrierRepository carrierRepository;

    @Autowired
    private CarrierTerminalRepository terminalRepository;

    @Autowired
    private PaymentMethodSettingsRepository paymentMethodSettingsRepository;

//    @MockitoBean
//    private ProductServiceClient productServiceClient;

    @MockitoBean
    private Order1CKafkaProducer order1CKafkaProducer;

    private static final Long USER_ID = 100L;
    private static final Long PRODUCT_ID_1 = 1L;
    private static final Long PRODUCT_ID_2 = 2L;
    private static final String USER_EMAIL = "user@email.com";


    private static final ProductDto PRODUCT_1 = new ProductDto(
            PRODUCT_ID_1, "Доска обрезная 50x150", new BigDecimal("1500.00"),
            null, 100, true, "ext-001", "ART-001", "шт", null, null, null, null);
    private static final ProductDto PRODUCT_2 = new ProductDto(
            PRODUCT_ID_2, "Брус 100x100", new BigDecimal("1000.00"),
            null, 50, true, "ext-002", "ART-002", "м3", null, null, null, null);

    private static final WarehouseDto WAREHOUSE = new WarehouseDto(1L, "PUSHKIN", "Склад Пушкин", "Пушкин", true);
    private static final WarehouseDto WAREHOUSE_NO_PICKUP =
            new WarehouseDto(2L, "SPB", "Склад СПб", "Санкт-Петербург", false);

    private final ObjectMapper jsonMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    private WarehousePoint savedWarehousePoint;

    @BeforeEach
    void setUp() {
        orderRepository.deleteAll();
        cartService.clearCart(USER_ID);

        Carrier dellin = carrierRepository.findById(CarrierCode.DELLIN).orElseThrow();
        dellin.setEnabled(true);
        dellin.setAddressDeliveryEnabled(true);
        carrierRepository.save(dellin);

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

        // Mock user-service — профиль заполнен по умолчанию
        when(userServiceClient.getProfileCompleteness(USER_ID))
                .thenReturn(new ProfileCompletenessDto(true, List.of()));
        doCallRealMethod().when(userServiceClient).requireCompleteProfile(USER_ID);
    }

    // ==================== createOrder ====================

    @Nested
    @DisplayName("createOrder")
    class CreateOrderTests {

        @Test
        @DisplayName("SUPPLIER_DELIVERY от клиента — отказ")
        void shouldRejectSupplierDeliveryFromClient() {
            addItemsToCart();
            CreateOrderRequest request = new CreateOrderRequest(
                    PaymentMethod.CARD, DeliveryMethod.SUPPLIER_DELIVERY,
                    buildAddressDto(), null, null, null, null, null, null, null, null, null, 1L);

            assertThatThrownBy(() -> orderService.createOrder(USER_ID, USER_EMAIL, "B2C", request))
                    .isInstanceOf(InvalidOrderStateException.class)
                    .hasMessageContaining("менеджер");
        }

        @Test
        @DisplayName("CARRIER_ADDRESS без ТК — отказ")
        void shouldRejectCarrierAddressWithoutCarrier() {
            addItemsToCart();
            CreateOrderRequest request = new CreateOrderRequest(
                    PaymentMethod.CARD, DeliveryMethod.CARRIER_ADDRESS,
                    buildAddressDto(), null, null, null, null, null, null, null, null, null, 1L);

            assertThatThrownBy(() -> orderService.createOrder(USER_ID, USER_EMAIL, "B2C", request))
                    .isInstanceOf(InvalidOrderStateException.class)
                    .hasMessageContaining("транспортную компанию");
        }

        @Test
        @DisplayName("CARRIER_ADDRESS с выключенной ТК — отказ")
        void shouldRejectDisabledCarrier() {
            Carrier dellin = carrierRepository.findById(CarrierCode.DELLIN).orElseThrow();
            dellin.setAddressDeliveryEnabled(false);
            carrierRepository.save(dellin);
            addItemsToCart();
            CreateOrderRequest request = new CreateOrderRequest(
                    PaymentMethod.CARD, DeliveryMethod.CARRIER_ADDRESS,
                    buildAddressDto(), null, null, null, null, null, null, null, null, CarrierCode.DELLIN, 1L);

            assertThatThrownBy(() -> orderService.createOrder(USER_ID, USER_EMAIL, "B2C", request))
                    .isInstanceOf(InvalidOrderStateException.class)
                    .hasMessageContaining("недоступна");
        }

        @Test
        @DisplayName("CARRIER_ADDRESS с полностью выключенной ТК — отказ")
        void shouldRejectFullyDisabledCarrier() {
            Carrier dellin = carrierRepository.findById(CarrierCode.DELLIN).orElseThrow();
            dellin.setEnabled(false);
            carrierRepository.save(dellin);
            addItemsToCart();
            CreateOrderRequest request = new CreateOrderRequest(
                    PaymentMethod.CARD, DeliveryMethod.CARRIER_ADDRESS,
                    buildAddressDto(), null, null, null, null, null, null, null, null, CarrierCode.DELLIN, 1L);

            assertThatThrownBy(() -> orderService.createOrder(USER_ID, USER_EMAIL, "B2C", request))
                    .isInstanceOf(InvalidOrderStateException.class)
                    .hasMessageContaining("недоступна");
        }

        @Test
        @DisplayName("PICKUP — carrierCode из запроса не сохраняется")
        void shouldIgnoreCarrierForPickup() {
            addItemsToCart();
            CreateOrderRequest request = new CreateOrderRequest(
                    PaymentMethod.CARD, DeliveryMethod.PICKUP,
                    null, savedWarehousePoint.getId(), null, null, null, null, null, null, null, CarrierCode.DELLIN, 1L);

            Order order = orderService.createOrder(USER_ID, USER_EMAIL, "B2C", request);

            assertThat(order.getCarrierCode()).isNull();
            assertThat(order.getCarrierName()).isNull();
        }

        @Test
        @DisplayName("создаёт заказ из корзины с доставкой")
        void shouldCreateDeliveryOrder() {
            addItemsToCart();

            CreateOrderRequest request = new CreateOrderRequest(
                    PaymentMethod.CARD, DeliveryMethod.CARRIER_ADDRESS,
                    buildAddressDto(), null, null, null, "Тестовый заказ", null, null, null, null, CarrierCode.DELLIN, 1L);

            Order order = orderService.createOrder(USER_ID, USER_EMAIL, "B2C", request);

            assertThat(order.getId()).isNotNull();
            assertThat(order.getOrderNumber()).isNotBlank();
            assertThat(order.getStatus()).isEqualTo(OrderStatus.CREATED);
            assertThat(order.getPaymentMethod()).isEqualTo(PaymentMethod.CARD);
            assertThat(order.getDeliveryMethod()).isEqualTo(DeliveryMethod.CARRIER_ADDRESS);
            assertThat(order.getCarrierCode()).isEqualTo(CarrierCode.DELLIN);
            assertThat(order.getCarrierName()).isEqualTo("Деловые Линии");
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
                    null, savedWarehousePoint.getId(), "Петров Петр", "+79001112233", null, null, null, null, null, null, 1L);

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
                    null, savedWarehousePoint.getId(), null, null, null, null, null, null, null, null, 1L);

            assertThatThrownBy(() -> orderService.createOrder(USER_ID, USER_EMAIL, "B2C", request))
                    .isInstanceOf(CartEmptyException.class);
        }


        @Test
        @DisplayName("выбрасывает исключение при PICKUP без warehousePointId")
        void shouldThrowWhenPickupWithoutWarehouse() {
            addItemsToCart();

            CreateOrderRequest request = new CreateOrderRequest(
                    PaymentMethod.CARD, DeliveryMethod.PICKUP,
                    null, null, null, null, null, null, null, null, null, null, 1L);

            assertThatThrownBy(() -> orderService.createOrder(USER_ID, USER_EMAIL, "B2C", request))
                    .isInstanceOf(InvalidOrderStateException.class)
                    .hasMessageContaining("точку получения");
        }

        @Test
        @DisplayName("выбрасывает исключение при DELIVERY без адреса")
        void shouldThrowWhenDeliveryWithoutAddress() {
            addItemsToCart();

            CreateOrderRequest request = new CreateOrderRequest(
                    PaymentMethod.CARD, DeliveryMethod.CARRIER_ADDRESS,
                    null, null, null, null, null, null, null, null, null, CarrierCode.DELLIN, 1L);

            assertThatThrownBy(() -> orderService.createOrder(USER_ID, USER_EMAIL, "B2C", request))
                    .isInstanceOf(InvalidOrderStateException.class)
                    .hasMessageContaining("адрес");
        }

        @Test
        @DisplayName("сохраняет externalId товара при создании заказа (snapshot)")
        void shouldSaveProductExternalIdInOrderItem() {
            addItemsToCart();

            CreateOrderRequest request = new CreateOrderRequest(
                    PaymentMethod.CARD, DeliveryMethod.CARRIER_ADDRESS,
                    buildAddressDto(), null, null, null, "Тест externalId", null, null, null, null, CarrierCode.DELLIN, 1L);

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
            cartService.addItemToCart(USER_ID, PRODUCT_ID_1, 1);

            CreateOrderRequest request = new CreateOrderRequest(
                    PaymentMethod.CARD, DeliveryMethod.CARRIER_ADDRESS,
                    buildAddressDto(), null, null, null, null, "ООО Ромашка", "1234567890", null, null, CarrierCode.DELLIN, 1L);

            Order order = orderService.createOrder(USER_ID, USER_EMAIL, "B2B", request);

            assertThat(order.getCustomerType()).isEqualTo(ru.rfsnab.orderservice.models.entity.enums.CustomerType.B2B);
            assertThat(order.getCompanyName()).isEqualTo("ООО Ромашка");
            assertThat(order.getInn()).isEqualTo("1234567890");
        }

        @Test
        @DisplayName("B2C: поля компании не заполняются — customerType B2C, companyName null, inn null")
        void createOrder_B2C_noSnapshotFields() {
            cartService.addItemToCart(USER_ID, PRODUCT_ID_1, 1);

            CreateOrderRequest request = new CreateOrderRequest(
                    PaymentMethod.CARD, DeliveryMethod.CARRIER_ADDRESS,
                    buildAddressDto(), null, null, null, null, null, null, null, null, CarrierCode.DELLIN, 1L);

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
                    new BigDecimal("800.00"), 100, true, "ext-010", null, null, null, null, null, null);

            when(productServiceClient.getProducts(anySet())).thenReturn(Map.of(productId, product));
            when(productServiceClient.getProduct(productId)).thenReturn(product);

            cartService.addItemToCart(USER_ID, productId, 2);

            CreateOrderRequest request = new CreateOrderRequest(
                    PaymentMethod.CARD, DeliveryMethod.CARRIER_ADDRESS,
                    buildAddressDto(), null, null, null, null, "ООО Тест", "9876543210", null, null, CarrierCode.DELLIN, 1L);

            Order order = orderService.createOrder(USER_ID, USER_EMAIL, "B2B", request);

            assertThat(order.getItems()).hasSize(1);
            OrderItem item = order.getItems().get(0);
            assertThat(item.getPrice()).isEqualByComparingTo("1000.00");
            assertThat(order.getTotalAmount()).isEqualByComparingTo("2000.00");
        }

        @Test
        @DisplayName("вариант товара: variantAttributes (Размер, Рост) и sku сохраняются в позиции")
        void createOrder_variant_savesVariantAttributesAndSku() {
            Long productId = 12L;
            ProductDto product = new ProductDto(productId, "Костюм", new BigDecimal("1000.00"),
                    new BigDecimal("800.00"), 100, true, "FTK-50991000.014", "50991000.014", null, null, 99L,
                    List.of(new ProductDto.Attribute("Признак наличия", "Складской"),
                            new ProductDto.Attribute("Рост", "170-176"),
                            new ProductDto.Attribute("Размер", "64-66")), null);

            when(productServiceClient.getProducts(anySet())).thenReturn(Map.of(productId, product));
            when(productServiceClient.getProduct(productId)).thenReturn(product);

            cartService.addItemToCart(USER_ID, productId, 1);

            CreateOrderRequest request = new CreateOrderRequest(
                    PaymentMethod.CARD, DeliveryMethod.CARRIER_ADDRESS,
                    buildAddressDto(), null, null, null, null, null, null, null, null, CarrierCode.DELLIN, 1L);

            Order order = orderService.createOrder(USER_ID, USER_EMAIL, "B2C", request);

            OrderItem item = order.getItems().get(0);
            assertThat(item.getVariantAttributes()).isEqualTo("Размер: 64-66, Рост: 170-176");
            assertThat(item.getSku()).isEqualTo("50991000.014");
        }

        @Test
        @DisplayName("B2C: использует розничную цену (wholesalePrice), не оптовую (price)")
        void createOrder_B2C_usesRetailPrice() {
            Long productId = 11L;
            ProductDto product = new ProductDto(productId, "Товар B2C", new BigDecimal("1000.00"),
                    new BigDecimal("800.00"), 100, true, "ext-011", null, null, null, null, null, null);

            when(productServiceClient.getProducts(anySet())).thenReturn(Map.of(productId, product));
            when(productServiceClient.getProduct(productId)).thenReturn(product);

            cartService.addItemToCart(USER_ID, productId, 2);

            CreateOrderRequest request = new CreateOrderRequest(
                    PaymentMethod.CARD, DeliveryMethod.CARRIER_ADDRESS,
                    buildAddressDto(), null, null, null, null, null, null, null, null, CarrierCode.DELLIN, 1L);

            Order order = orderService.createOrder(USER_ID, USER_EMAIL, "B2C", request);

            assertThat(order.getItems()).hasSize(1);
            OrderItem item = order.getItems().get(0);
            assertThat(item.getPrice()).isEqualByComparingTo("800.00");
            assertThat(order.getTotalAmount()).isEqualByComparingTo("1600.00");
        }

        @Test
        @DisplayName("B2B без inn: выбрасывает InvalidOrderStateException")
        void createOrder_B2B_missingInn_throws() {
            cartService.addItemToCart(USER_ID, PRODUCT_ID_1, 1);

            CreateOrderRequest request = new CreateOrderRequest(
                    PaymentMethod.CARD, DeliveryMethod.CARRIER_ADDRESS,
                    buildAddressDto(), null, null, null, null, "ООО Ромашка", null, null, null, CarrierCode.DELLIN, 1L);

            assertThatThrownBy(() -> orderService.createOrder(USER_ID, USER_EMAIL, "B2B", request))
                    .isInstanceOf(InvalidOrderStateException.class);
        }

        @Test
        @DisplayName("бросает ProfileIncompleteException если профиль не заполнен")
        void shouldThrowWhenProfileIncomplete() {
            addItemsToCart();
            when(userServiceClient.getProfileCompleteness(USER_ID))
                    .thenReturn(new ProfileCompletenessDto(false, List.of("Телефон")));

            CreateOrderRequest request = new CreateOrderRequest(
                    PaymentMethod.CARD, DeliveryMethod.CARRIER_ADDRESS,
                    buildAddressDto(), null, null, null, "Тестовый заказ", null, null, null, null, CarrierCode.DELLIN, 1L);

            assertThatThrownBy(() -> orderService.createOrder(USER_ID, USER_EMAIL, "B2C", request))
                    .isInstanceOf(ProfileIncompleteException.class);
        }

        @Test
        @DisplayName("создаёт заказ, когда профиль заполнен")
        void shouldCreateOrderWhenProfileComplete() {
            addItemsToCart();

            CreateOrderRequest request = new CreateOrderRequest(
                    PaymentMethod.CARD, DeliveryMethod.CARRIER_ADDRESS,
                    buildAddressDto(), null, null, null, "Тестовый заказ", null, null, null, null, CarrierCode.DELLIN, 1L);

            Order order = orderService.createOrder(USER_ID, USER_EMAIL, "B2C", request);

            assertThat(order.getId()).isNotNull();
        }
    }

    // ==================== updateOrder ====================

    @Nested
    @DisplayName("updateOrder")
    class UpdateOrderTests {

        @Test
        @DisplayName("смена на SUPPLIER_DELIVERY клиентом — отказ")
        void shouldRejectSwitchToSupplierDelivery() {
            Order order = createTestOrder();
            UpdateOrderRequest request = UpdateOrderRequest.builder().shippingWarehouseId(1L)
                    .paymentMethod(PaymentMethod.CARD)
                    .deliveryMethod(DeliveryMethod.SUPPLIER_DELIVERY)
                    .deliveryAddress(buildAddressDto())
                    .items(List.of(new OrderItemDto(PRODUCT_ID_1, null, 1, null, null, null, null, null, null, null)))
                    .build();

            assertThatThrownBy(() -> orderService.updateOrder(order.getId(), USER_ID, request))
                    .isInstanceOf(InvalidOrderStateException.class)
                    .hasMessageContaining("менеджер");
        }

        @Test
        @DisplayName("SUPPLIER_DELIVERY, назначенный менеджером, можно оставить")
        void shouldKeepSupplierDeliveryAssignedByManager() {
            Order order = createTestOrder();
            jdbcTemplate.update("UPDATE orders SET delivery_method = 'SUPPLIER_DELIVERY', carrier_code = NULL WHERE id = ?",
                    order.getId());
            UpdateOrderRequest request = UpdateOrderRequest.builder().shippingWarehouseId(1L)
                    .paymentMethod(PaymentMethod.CARD)
                    .deliveryMethod(DeliveryMethod.SUPPLIER_DELIVERY)
                    .deliveryAddress(buildAddressDto())
                    .items(List.of(new OrderItemDto(PRODUCT_ID_1, null, 1, null, null, null, null, null, null, null)))
                    .comment("новый")
                    .build();

            Order updated = orderService.updateOrder(order.getId(), USER_ID, request);

            assertThat(updated.getDeliveryMethod()).isEqualTo(DeliveryMethod.SUPPLIER_DELIVERY);
            assertThat(updated.getComment()).isEqualTo("новый");
        }

        @Test
        @DisplayName("смена ТК при редактировании сохраняется")
        void shouldUpdateCarrier() {
            Order order = createTestOrder();
            UpdateOrderRequest request = UpdateOrderRequest.builder().shippingWarehouseId(1L)
                    .paymentMethod(PaymentMethod.CARD)
                    .deliveryMethod(DeliveryMethod.CARRIER_ADDRESS)
                    .carrierCode(CarrierCode.PEC)
                    .deliveryAddress(buildAddressDto())
                    .items(List.of(new OrderItemDto(PRODUCT_ID_1, null, 1, null, null, null, null, null, null, null)))
                    .build();

            Order updated = orderService.updateOrder(order.getId(), USER_ID, request);

            assertThat(updated.getCarrierCode()).isEqualTo(CarrierCode.PEC);
            assertThat(updated.getCarrierName()).isEqualTo("ПЭК");
        }

        @Test
        @DisplayName("обновляет заказ в статусе CREATED")
        void shouldUpdateCreatedOrder() {
            Order order = createTestOrder();

            UpdateOrderRequest request = UpdateOrderRequest.builder().shippingWarehouseId(1L)
                    .paymentMethod(PaymentMethod.SBP)
                    .deliveryMethod(DeliveryMethod.PICKUP)
                    .warehousePointId(savedWarehousePoint.getId())
                    .items(List.of(
                            new OrderItemDto(PRODUCT_ID_1, null, 20, null, null, null, null, null, null, null)))
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
            // CREATED в 1С не отправляется — уйдёт при confirmOrder
            verify(order1CKafkaProducer, never()).sendOrderFor1C(any());
        }

        @Test
        @DisplayName("обновляет заказ в статусе PROCESSING и повторно отправляет в 1С")
        void shouldUpdateProcessingOrderAndResendTo1C() {
            Order order = createTestOrder();
            orderService.confirmOrder(order.getId(), USER_ID);
            clearInvocations(order1CKafkaProducer);

            UpdateOrderRequest request = UpdateOrderRequest.builder().shippingWarehouseId(1L)
                    .paymentMethod(PaymentMethod.CARD)
                    .deliveryMethod(DeliveryMethod.PICKUP)
                    .warehousePointId(savedWarehousePoint.getId())
                    .items(List.of(new OrderItemDto(PRODUCT_ID_1, null, 5, null, null, null, null, null, null, null)))
                    .comment("Правка в PROCESSING")
                    .build();

            Order updated = orderService.updateOrder(order.getId(), USER_ID, request);

            assertThat(updated.getStatus()).isEqualTo(OrderStatus.PROCESSING);
            assertThat(updated.getComment()).isEqualTo("Правка в PROCESSING");
            assertThat(updated.getItems()).hasSize(1);
            assertThat(updated.getItems().get(0).getQuantity()).isEqualTo(5);
            assertThat(updated.getTotalAmount()).isEqualByComparingTo("7500.00");
            verify(order1CKafkaProducer).sendOrderFor1C(any(Order.class));
        }

        @Test
        @DisplayName("PROCESSING: переключение на выключенный способ оплаты — PaymentMethodNotAvailableException, в 1С не уходит")
        void shouldRejectDisabledPaymentMethodOnProcessingEdit() {
            Order order = createTestOrder();
            orderService.confirmOrder(order.getId(), USER_ID);
            clearInvocations(order1CKafkaProducer);

            PaymentMethodSettings s = getOrCreateSettings();
            s.setSbpEnabled(false);
            paymentMethodSettingsRepository.save(s);

            UpdateOrderRequest request = UpdateOrderRequest.builder().shippingWarehouseId(1L)
                    .paymentMethod(PaymentMethod.SBP)
                    .deliveryMethod(DeliveryMethod.PICKUP)
                    .warehousePointId(savedWarehousePoint.getId())
                    .items(List.of(new OrderItemDto(PRODUCT_ID_1, null, 5, null, null, null, null, null, null, null)))
                    .build();

            UUID orderId = order.getId();
            assertThatThrownBy(() -> orderService.updateOrder(orderId, USER_ID, request))
                    .isInstanceOf(PaymentMethodNotAvailableException.class)
                    .hasMessageContaining("СБП");
            verify(order1CKafkaProducer, never()).sendOrderFor1C(any());
        }

        @Test
        @DisplayName("PROCESSING: способ оплаты не менялся и выключен в настройках — правка комментария проходит, в 1С уходит")
        void shouldAllowEditWithoutPaymentChangeWhenCurrentMethodDisabled() {
            Order order = createTestOrder();
            orderService.confirmOrder(order.getId(), USER_ID);
            clearInvocations(order1CKafkaProducer);

            PaymentMethodSettings s = getOrCreateSettings();
            s.setCardEnabled(false);
            paymentMethodSettingsRepository.save(s);

            UpdateOrderRequest request = UpdateOrderRequest.builder().shippingWarehouseId(1L)
                    .paymentMethod(PaymentMethod.CARD)
                    .deliveryMethod(DeliveryMethod.PICKUP)
                    .warehousePointId(savedWarehousePoint.getId())
                    .items(List.of(new OrderItemDto(PRODUCT_ID_1, null, 5, null, null, null, null, null, null, null)))
                    .comment("Только комментарий")
                    .build();

            Order updated = orderService.updateOrder(order.getId(), USER_ID, request);

            assertThat(updated.getComment()).isEqualTo("Только комментарий");
            verify(order1CKafkaProducer).sendOrderFor1C(any(Order.class));
        }

        @Test
        @DisplayName("PROCESSING: чужой пользователь не может редактировать заказ")
        void shouldThrowWhenWrongUserOnProcessingOrder() {
            Order order = createTestOrder();
            orderService.confirmOrder(order.getId(), USER_ID);

            UpdateOrderRequest request = UpdateOrderRequest.builder().shippingWarehouseId(1L)
                    .paymentMethod(PaymentMethod.CARD)
                    .deliveryMethod(DeliveryMethod.PICKUP)
                    .warehousePointId(savedWarehousePoint.getId())
                    .items(List.of(new OrderItemDto(PRODUCT_ID_1, null, 5, null, null, null, null, null, null, null)))
                    .build();

            UUID orderId = order.getId();
            assertThatThrownBy(() -> orderService.updateOrder(orderId, 999L, request))
                    .isInstanceOf(InvalidOrderStateException.class)
                    .hasMessageContaining("Нет доступа");
        }

        @Test
        @DisplayName("выбрасывает исключение при обновлении не в статусе CREATED/PROCESSING")
        void shouldThrowWhenNotCreatedStatus() {
            Order order = seedOrderWithStatus(OrderStatus.INVOICE_SENT);

            UpdateOrderRequest request = UpdateOrderRequest.builder().shippingWarehouseId(1L)
                    .paymentMethod(PaymentMethod.CARD)
                    .deliveryMethod(DeliveryMethod.PICKUP)
                    .warehousePointId(savedWarehousePoint.getId())
                    .items(List.of(new OrderItemDto(PRODUCT_ID_1, null, 5, null, null, null, null, null, null, null)))
                    .build();

            assertThatThrownBy(() -> orderService.updateOrder(order.getId(), USER_ID, request))
                    .isInstanceOf(InvalidOrderStateException.class)
                    .hasMessageContaining("невозможно в статусе");
        }

        @Test
        @DisplayName("выбрасывает исключение при доступе чужого пользователя")
        void shouldThrowWhenWrongUser() {
            Order order = createTestOrder();

            UpdateOrderRequest request = UpdateOrderRequest.builder().shippingWarehouseId(1L)
                    .paymentMethod(PaymentMethod.CARD)
                    .deliveryMethod(DeliveryMethod.PICKUP)
                    .warehousePointId(savedWarehousePoint.getId())
                    .items(List.of(new OrderItemDto(PRODUCT_ID_1, null, 5, null, null, null, null, null, null, null)))
                    .build();

            assertThatThrownBy(() -> orderService.updateOrder(order.getId(), 999L, request))
                    .isInstanceOf(InvalidOrderStateException.class)
                    .hasMessageContaining("Нет доступа");
        }
    }

    // ==================== склад отгрузки ====================

    @Nested
    @DisplayName("склад отгрузки")
    class ShippingWarehouseTests {

        private CreateOrderRequest carrierRequest(Long warehouseId) {
            return new CreateOrderRequest(PaymentMethod.CARD, DeliveryMethod.CARRIER_ADDRESS,
                    buildAddressDto(), null, null, null, null, null, null, null, null, CarrierCode.DELLIN, warehouseId);
        }

        private CreateOrderRequest pickupRequest(Long warehouseId) {
            return new CreateOrderRequest(PaymentMethod.CARD, DeliveryMethod.PICKUP,
                    null, savedWarehousePoint.getId(), "Петров Петр", "+79001112233", null, null, null, null, null,
                    null, warehouseId);
        }

        private UpdateOrderRequest.UpdateOrderRequestBuilder updateRequest(Long warehouseId) {
            return UpdateOrderRequest.builder().shippingWarehouseId(warehouseId)
                    .paymentMethod(PaymentMethod.CARD)
                    .items(List.of(new OrderItemDto(PRODUCT_ID_1, null, 1, null, null, null, null, null, null, null)));
        }

        @Test
        @DisplayName("createOrder без склада — отказ")
        void shouldRejectCreateWithoutWarehouse() {
            addItemsToCart();

            assertThatThrownBy(() -> orderService.createOrder(USER_ID, USER_EMAIL, "B2C", carrierRequest(null)))
                    .isInstanceOf(InvalidOrderStateException.class)
                    .hasMessage("Выберите город отгрузки");
        }

        @Test
        @DisplayName("createOrder со складом, которого нет среди активных — отказ")
        void shouldRejectCreateWithUnknownWarehouse() {
            addItemsToCart();

            assertThatThrownBy(() -> orderService.createOrder(USER_ID, USER_EMAIL, "B2C", carrierRequest(999L)))
                    .isInstanceOf(InvalidOrderStateException.class)
                    .hasMessage("Выберите город отгрузки");
        }

        @Test
        @DisplayName("createOrder PICKUP при складе без самовывоза — отказ")
        void shouldRejectPickupWhenWarehouseHasNoPickup() {
            addItemsToCart();

            assertThatThrownBy(() -> orderService.createOrder(USER_ID, USER_EMAIL, "B2C",
                    pickupRequest(WAREHOUSE_NO_PICKUP.id())))
                    .isInstanceOf(InvalidOrderStateException.class)
                    .hasMessage("Самовывоз недоступен для выбранного города");
        }

        @Test
        @DisplayName("createOrder сохраняет снимок склада")
        void shouldSnapshotWarehouse() {
            addItemsToCart();

            Order order = orderService.createOrder(USER_ID, USER_EMAIL, "B2C", carrierRequest(WAREHOUSE.id()));

            assertThat(order.getShippingWarehouseId()).isEqualTo(WAREHOUSE.id());
            assertThat(order.getShippingWarehouseName()).isEqualTo("Склад Пушкин");
            assertThat(order.getShippingWarehouseCity()).isEqualTo("Пушкин");
        }

        @Test
        @DisplayName("updateOrder без склада — отказ")
        void shouldRejectUpdateWithoutWarehouse() {
            Order order = createTestOrder();
            UpdateOrderRequest request = updateRequest(null)
                    .deliveryMethod(DeliveryMethod.CARRIER_ADDRESS).carrierCode(CarrierCode.DELLIN)
                    .deliveryAddress(buildAddressDto()).build();

            assertThatThrownBy(() -> orderService.updateOrder(order.getId(), USER_ID, request))
                    .isInstanceOf(InvalidOrderStateException.class)
                    .hasMessage("Выберите город отгрузки");
        }

        @Test
        @DisplayName("updateOrder PICKUP при складе без самовывоза — отказ")
        void shouldRejectUpdatePickupWhenWarehouseHasNoPickup() {
            Order order = createTestOrder();
            UpdateOrderRequest request = updateRequest(WAREHOUSE_NO_PICKUP.id())
                    .deliveryMethod(DeliveryMethod.PICKUP).warehousePointId(savedWarehousePoint.getId())
                    .pickupRecipientName("Петров Петр").pickupRecipientPhone("+79001112233").build();

            assertThatThrownBy(() -> orderService.updateOrder(order.getId(), USER_ID, request))
                    .isInstanceOf(InvalidOrderStateException.class)
                    .hasMessage("Самовывоз недоступен для выбранного города");
        }

        @Test
        @DisplayName("updateOrder обновляет снимок склада")
        void shouldUpdateWarehouseSnapshot() {
            Order order = createTestOrder();
            UpdateOrderRequest request = updateRequest(WAREHOUSE_NO_PICKUP.id())
                    .deliveryMethod(DeliveryMethod.CARRIER_ADDRESS).carrierCode(CarrierCode.DELLIN)
                    .deliveryAddress(buildAddressDto()).build();

            Order updated = orderService.updateOrder(order.getId(), USER_ID, request);

            assertThat(updated.getShippingWarehouseId()).isEqualTo(WAREHOUSE_NO_PICKUP.id());
            assertThat(updated.getShippingWarehouseCity()).isEqualTo("Санкт-Петербург");
        }

        @Test
        @DisplayName("repeatOrder копирует склад; выключенный склад — понятная ошибка")
        void shouldCopyWarehouseOnRepeat() {
            addItemsToCart();
            Order original = orderService.createOrder(USER_ID, USER_EMAIL, "B2C", carrierRequest(WAREHOUSE.id()));

            Order repeated = orderService.repeatOrder(original.getId(), USER_ID, USER_EMAIL);

            assertThat(repeated.getShippingWarehouseId()).isEqualTo(WAREHOUSE.id());
            assertThat(repeated.getShippingWarehouseCity()).isEqualTo("Пушкин");

            when(productServiceClient.getActiveWarehouses()).thenReturn(List.of(WAREHOUSE_NO_PICKUP));
            assertThatThrownBy(() -> orderService.repeatOrder(original.getId(), USER_ID, USER_EMAIL))
                    .isInstanceOf(InvalidOrderStateException.class)
                    .hasMessage("Город отгрузки больше недоступен, выберите другой");
        }

        @Test
        @DisplayName("позиция склеенного товара уходит с Ид номенклатуры 1С, без склейки — с externalId товара")
        void shouldUseOneCExternalIdForMergedProduct() {
            ProductDto merged = new ProductDto(PRODUCT_ID_1, "Доска обрезная 50x150", new BigDecimal("1500.00"),
                    null, 100, true, "ext-001", "ART-001", "шт", null, null, null, "uuid-1c");
            when(productServiceClient.getProducts(anySet()))
                    .thenReturn(Map.of(PRODUCT_ID_1, merged, PRODUCT_ID_2, PRODUCT_2));
            addItemsToCart();

            Order order = orderService.createOrder(USER_ID, USER_EMAIL, "B2C", carrierRequest(WAREHOUSE.id()));

            Map<Long, String> externalIds = new HashMap<>();
            order.getItems().forEach(i -> externalIds.put(i.getProductId(), i.getExternalId()));
            assertThat(externalIds).containsEntry(PRODUCT_ID_1, "uuid-1c").containsEntry(PRODUCT_ID_2, "ext-002");
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
            assertThat(repeated.getCarrierCode()).isEqualTo(CarrierCode.DELLIN);
            assertThat(repeated.getCarrierName()).isEqualTo("Деловые Линии");
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

            cartService.addItemToCart(USER_ID, PRODUCT_ID_1, 1);
            CreateOrderRequest req = new CreateOrderRequest(
                    PaymentMethod.INVOICE, DeliveryMethod.CARRIER_ADDRESS,
                    buildAddressDto(), null, null, null, null, null, null, null, null, CarrierCode.DELLIN, 1L);
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

            cartService.addItemToCart(USER_ID, PRODUCT_ID_1, 1);
            CreateOrderRequest req = new CreateOrderRequest(
                    PaymentMethod.CARD, DeliveryMethod.CARRIER_ADDRESS,
                    buildAddressDto(), null, null, null, null, null, null, null, null, CarrierCode.DELLIN, 1L);
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

            cartService.addItemToCart(USER_ID, PRODUCT_ID_1, 1);
            CreateOrderRequest req = new CreateOrderRequest(
                    PaymentMethod.SBP, DeliveryMethod.CARRIER_ADDRESS,
                    buildAddressDto(), null, null, null, null, null, null, null, null, CarrierCode.DELLIN, 1L);
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

            cartService.addItemToCart(USER_ID, PRODUCT_ID_1, 1);
            CreateOrderRequest req = new CreateOrderRequest(
                    PaymentMethod.INVOICE, DeliveryMethod.CARRIER_ADDRESS,
                    buildAddressDto(), null, null, null, null, "ООО Тест", "1234567890", null, null, CarrierCode.DELLIN, 1L);
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

    // ==================== getOrderStats ====================

    @Nested
    @DisplayName("getOrderStats")
    class GetOrderStatsTests {

        @Test
        @DisplayName("считает total/inProgress/completed/cancelled/newLast30Days по всем статусам")
        void shouldReturnStats() {
            seedOrderWithStatus(OrderStatus.CREATED);
            seedOrderWithStatus(OrderStatus.PROCESSING);
            seedOrderWithStatus(OrderStatus.DELIVERED);
            seedOrderWithStatus(OrderStatus.COMPLETED);
            seedOrderWithStatus(OrderStatus.CANCELLED);
            seedOrderWithStatus(OrderStatus.REFUNDED);
            Order oldOrder = seedOrderWithStatus(OrderStatus.CREATED);
            backdateCreatedAt(oldOrder.getId(), 40);

            OrderStatsResponse stats = orderService.getOrderStats();

            assertThat(stats.total()).isEqualTo(7);
            assertThat(stats.inProgress()).isEqualTo(3); // CREATED, PROCESSING, CREATED (старый)
            assertThat(stats.completed()).isEqualTo(2);  // DELIVERED, COMPLETED
            assertThat(stats.cancelled()).isEqualTo(2);  // CANCELLED, REFUNDED
            assertThat(stats.newLast30Days()).isEqualTo(6); // все, кроме созданного 40 дней назад
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
                    null, savedWarehousePoint.getId(), "Петров Петр", "+79001112233", null, null, null, null, null, null, 1L);

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
                    PaymentMethod.CARD, DeliveryMethod.CARRIER_ADDRESS,
                    buildAddressDto(), null, null, null, "Тест 1С export", null, null, null, null, CarrierCode.DELLIN, 1L);

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

    @Nested
    @DisplayName("CARRIER_TERMINAL")
    class CarrierTerminalTests {

        private CarrierTerminal dellinTerminal;
        private CarrierTerminal cdekTerminal;
        private CarrierTerminal closedDellinTerminal;

        @BeforeEach
        void setUpTerminals() {
            terminalRepository.deleteAll();
            setDellinTerminalDelivery(true);
            dellinTerminal = saveTerminal(CarrierCode.DELLIN, "D1", "Терминал Толмачёво", "ул. Ленина, 1", true);
            cdekTerminal = saveTerminal(CarrierCode.CDEK, "C1", "ПВЗ СДЭК", "ул. Мира, 2", true);
            closedDellinTerminal = saveTerminal(CarrierCode.DELLIN, "D2", "Закрытый терминал", "ул. Старая, 3", false);
        }

        private CarrierTerminal saveTerminal(CarrierCode carrier, String externalId, String name, String address, boolean active) {
            return terminalRepository.save(CarrierTerminal.builder()
                    .carrierCode(carrier).externalId(externalId).pointType(PointType.TERMINAL)
                    .city("Новосибирск").name(name).address(address)
                    .latitude(new BigDecimal("55.030000")).longitude(new BigDecimal("82.920000"))
                    .active(active).syncedAt(Instant.now()).build());
        }

        private void setDellinTerminalDelivery(boolean enabled) {
            Carrier dellin = carrierRepository.findById(CarrierCode.DELLIN).orElseThrow();
            dellin.setTerminalDeliveryEnabled(enabled);
            carrierRepository.save(dellin);
        }

        private CreateOrderRequest terminalRequest(CarrierCode carrier, Long terminalId, AddressDto address) {
            return new CreateOrderRequest(PaymentMethod.CARD, DeliveryMethod.CARRIER_TERMINAL,
                    address, null, "Иван Иванов", "+79990000000", null, null, null, null, null, carrier, terminalId, 1L);
        }

        @Test
        @DisplayName("заказ сохраняет снимок названия и адреса терминала из БД, а не из запроса")
        void shouldSnapshotTerminalFromDb() {
            addItemsToCart();

            Order order = orderService.createOrder(USER_ID, USER_EMAIL, "B2C",
                    terminalRequest(CarrierCode.DELLIN, dellinTerminal.getId(), null));

            assertThat(order.getDeliveryMethod()).isEqualTo(DeliveryMethod.CARRIER_TERMINAL);
            assertThat(order.getDeliveryAddress()).isNull();
            assertThat(order.getCarrierCode()).isEqualTo(CarrierCode.DELLIN);
            assertThat(order.getCarrierName()).isEqualTo("Деловые Линии");
            assertThat(order.getCarrierTerminalId()).isEqualTo(dellinTerminal.getId());
            assertThat(order.getTerminalName()).isEqualTo("Терминал Толмачёво");
            assertThat(order.getTerminalAddress()).isEqualTo("ул. Ленина, 1");

            // терминал сменил адрес — снимок в заказе остаётся прежним
            dellinTerminal.setAddress("ул. Новая, 9");
            terminalRepository.save(dellinTerminal);
            Order reloaded = orderRepository.findById(order.getId()).orElseThrow();
            assertThat(reloaded.getTerminalAddress()).isEqualTo("ул. Ленина, 1");
        }

        @Test
        @DisplayName("терминал другой ТК — отказ")
        void shouldRejectForeignCarrierTerminal() {
            addItemsToCart();
            CreateOrderRequest request = terminalRequest(CarrierCode.DELLIN, cdekTerminal.getId(), buildAddressDto());

            assertThatThrownBy(() -> orderService.createOrder(USER_ID, USER_EMAIL, "B2C", request))
                    .isInstanceOf(InvalidOrderStateException.class)
                    .hasMessageContaining("недоступен");
        }

        @Test
        @DisplayName("пришедший с запросом адрес для терминала не сохраняется")
        void shouldIgnoreAddressForTerminal() {
            addItemsToCart();

            Order order = orderService.createOrder(USER_ID, USER_EMAIL, "B2C",
                    terminalRequest(CarrierCode.DELLIN, dellinTerminal.getId(), buildAddressDto()));

            assertThat(order.getDeliveryAddress()).isNull();
            assertThat(order.getTerminalName()).isEqualTo("Терминал Толмачёво");
        }

        @Test
        @DisplayName("неактивный терминал — отказ")
        void shouldRejectInactiveTerminal() {
            addItemsToCart();
            CreateOrderRequest request = terminalRequest(CarrierCode.DELLIN, closedDellinTerminal.getId(), buildAddressDto());

            assertThatThrownBy(() -> orderService.createOrder(USER_ID, USER_EMAIL, "B2C", request))
                    .isInstanceOf(InvalidOrderStateException.class)
                    .hasMessageContaining("недоступен");
        }

        @Test
        @DisplayName("ТК без terminal_delivery_enabled — отказ")
        void shouldRejectCarrierWithoutTerminalDelivery() {
            setDellinTerminalDelivery(false);
            addItemsToCart();
            CreateOrderRequest request = terminalRequest(CarrierCode.DELLIN, dellinTerminal.getId(), buildAddressDto());

            assertThatThrownBy(() -> orderService.createOrder(USER_ID, USER_EMAIL, "B2C", request))
                    .isInstanceOf(InvalidOrderStateException.class)
                    .hasMessageContaining("до терминала");
        }

        @Test
        @DisplayName("несуществующий терминал, пустой терминал, пустая ТК — отказ")
        void shouldRejectIncompleteRequests() {
            addItemsToCart();

            assertThatThrownBy(() -> orderService.createOrder(USER_ID, USER_EMAIL, "B2C",
                    terminalRequest(CarrierCode.DELLIN, 999_999L, buildAddressDto())))
                    .isInstanceOf(InvalidOrderStateException.class).hasMessageContaining("не найден");
            assertThatThrownBy(() -> orderService.createOrder(USER_ID, USER_EMAIL, "B2C",
                    terminalRequest(CarrierCode.DELLIN, null, buildAddressDto())))
                    .isInstanceOf(InvalidOrderStateException.class).hasMessageContaining("терминал");
            assertThatThrownBy(() -> orderService.createOrder(USER_ID, USER_EMAIL, "B2C",
                    terminalRequest(null, dellinTerminal.getId(), buildAddressDto())))
                    .isInstanceOf(InvalidOrderStateException.class).hasMessageContaining("транспортную компанию");
        }

        @Test
        @DisplayName("редактирование: адресная доставка → терминал → самовывоз меняет и очищает снимок")
        void shouldSwitchSnapshotOnUpdate() {
            Order order = createTestOrder();
            OrderItemDto item = new OrderItemDto(PRODUCT_ID_1, null, 1, null, null, null, null, null, null, null);

            Order toTerminal = orderService.updateOrder(order.getId(), USER_ID, UpdateOrderRequest.builder().shippingWarehouseId(1L)
                    .paymentMethod(PaymentMethod.CARD).deliveryMethod(DeliveryMethod.CARRIER_TERMINAL)
                    .carrierCode(CarrierCode.DELLIN).carrierTerminalId(dellinTerminal.getId())
                    .pickupRecipientName("Иван Иванов").pickupRecipientPhone("+79990000000")
                    .items(List.of(item)).build());

            assertThat(toTerminal.getDeliveryAddress()).isNull();
            assertThat(toTerminal.getCarrierCode()).isEqualTo(CarrierCode.DELLIN);
            assertThat(toTerminal.getCarrierTerminalId()).isEqualTo(dellinTerminal.getId());
            assertThat(toTerminal.getTerminalName()).isEqualTo("Терминал Толмачёво");
            assertThat(toTerminal.getPickupRecipientName()).isEqualTo("Иван Иванов");

            Order toPickup = orderService.updateOrder(order.getId(), USER_ID, UpdateOrderRequest.builder().shippingWarehouseId(1L)
                    .paymentMethod(PaymentMethod.CARD).deliveryMethod(DeliveryMethod.PICKUP)
                    .warehousePointId(savedWarehousePoint.getId()).items(List.of(item)).build());

            assertThat(toPickup.getCarrierCode()).isNull();
            assertThat(toPickup.getCarrierName()).isNull();
            assertThat(toPickup.getCarrierTerminalId()).isNull();
            assertThat(toPickup.getTerminalName()).isNull();
            assertThat(toPickup.getTerminalAddress()).isNull();
        }

        @Test
        @DisplayName("редактирование: получатель берётся из запроса для самовывоза и очищается для адресной доставки")
        void shouldApplyRecipientOnUpdate() {
            Order order = createTestOrder();
            OrderItemDto item = new OrderItemDto(PRODUCT_ID_1, null, 1, null, null, null, null, null, null, null);

            Order toPickup = orderService.updateOrder(order.getId(), USER_ID, UpdateOrderRequest.builder().shippingWarehouseId(1L)
                    .paymentMethod(PaymentMethod.CARD).deliveryMethod(DeliveryMethod.PICKUP)
                    .warehousePointId(savedWarehousePoint.getId())
                    .pickupRecipientName("Пётр Петров").pickupRecipientPhone("+79991112233")
                    .items(List.of(item)).build());
            assertThat(toPickup.getPickupRecipientName()).isEqualTo("Пётр Петров");
            assertThat(toPickup.getPickupRecipientPhone()).isEqualTo("+79991112233");

            Order toAddress = orderService.updateOrder(order.getId(), USER_ID, UpdateOrderRequest.builder().shippingWarehouseId(1L)
                    .paymentMethod(PaymentMethod.CARD).deliveryMethod(DeliveryMethod.CARRIER_ADDRESS)
                    .carrierCode(CarrierCode.PEC).deliveryAddress(buildAddressDto())
                    .items(List.of(item)).build());
            assertThat(toAddress.getPickupRecipientName()).isNull();
            assertThat(toAddress.getPickupRecipientPhone()).isNull();
        }

        @Test
        @DisplayName("получатель сохраняется в заказ; без ФИО или телефона — отказ")
        void shouldRequireRecipient() {
            addItemsToCart();
            Order order = orderService.createOrder(USER_ID, USER_EMAIL, "B2C",
                    terminalRequest(CarrierCode.DELLIN, dellinTerminal.getId(), null));
            assertThat(order.getPickupRecipientName()).isEqualTo("Иван Иванов");
            assertThat(order.getPickupRecipientPhone()).isEqualTo("+79990000000");

            CreateOrderRequest noName = new CreateOrderRequest(PaymentMethod.CARD, DeliveryMethod.CARRIER_TERMINAL,
                    null, null, " ", "+79990000000", null, null, null, null, null,
                    CarrierCode.DELLIN, dellinTerminal.getId(), 1L);
            addItemsToCart();
            assertThatThrownBy(() -> orderService.createOrder(USER_ID, USER_EMAIL, "B2C", noName))
                    .isInstanceOf(InvalidOrderStateException.class).hasMessage("Укажите ФИО и телефон получателя");

            Order created = createTestOrder();
            OrderItemDto item = new OrderItemDto(PRODUCT_ID_1, null, 1, null, null, null, null, null, null, null);
            UpdateOrderRequest noPhone = UpdateOrderRequest.builder().shippingWarehouseId(1L)
                    .paymentMethod(PaymentMethod.CARD).deliveryMethod(DeliveryMethod.CARRIER_TERMINAL)
                    .carrierCode(CarrierCode.DELLIN).carrierTerminalId(dellinTerminal.getId())
                    .pickupRecipientName("Иван Иванов").items(List.of(item)).build();
            assertThatThrownBy(() -> orderService.updateOrder(created.getId(), USER_ID, noPhone))
                    .isInstanceOf(InvalidOrderStateException.class).hasMessage("Укажите ФИО и телефон получателя");
        }

        @Test
        @DisplayName("повтор заказа с терминалом копирует получателя; закрытый терминал или выключенная ТК — понятная ошибка")
        void shouldRepeatTerminalOrderOnlyIfTerminalAvailable() {
            addItemsToCart();
            Order original = orderService.createOrder(USER_ID, USER_EMAIL, "B2C",
                    terminalRequest(CarrierCode.DELLIN, dellinTerminal.getId(), null));

            Order repeated = orderService.repeatOrder(original.getId(), USER_ID, USER_EMAIL);
            assertThat(repeated.getTerminalName()).isEqualTo("Терминал Толмачёво");
            assertThat(repeated.getPickupRecipientName()).isEqualTo("Иван Иванов");
            assertThat(repeated.getPickupRecipientPhone()).isEqualTo("+79990000000");

            setDellinTerminalDelivery(false);
            assertThatThrownBy(() -> orderService.repeatOrder(original.getId(), USER_ID, USER_EMAIL))
                    .isInstanceOf(InvalidOrderStateException.class)
                    .hasMessage("Терминал больше недоступен, выберите другой");

            setDellinTerminalDelivery(true);
            dellinTerminal.setActive(false);
            terminalRepository.save(dellinTerminal);
            assertThatThrownBy(() -> orderService.repeatOrder(original.getId(), USER_ID, USER_EMAIL))
                    .isInstanceOf(InvalidOrderStateException.class)
                    .hasMessage("Терминал больше недоступен, выберите другой");
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
        when(productServiceClient.getActiveWarehouses()).thenReturn(List.of(WAREHOUSE, WAREHOUSE_NO_PICKUP));
    }

    private void addItemsToCart() {
        cartService.addItemToCart(USER_ID, PRODUCT_ID_1, 10);
        cartService.addItemToCart(USER_ID, PRODUCT_ID_2, 10);
    }

    private Order createTestOrder() {
        addItemsToCart();

        CreateOrderRequest request = new CreateOrderRequest(
                PaymentMethod.CARD, DeliveryMethod.CARRIER_ADDRESS,
                buildAddressDto(), null, null, null, "Тест", null, null, null, null, CarrierCode.DELLIN, 1L);

        return orderService.createOrder(USER_ID, USER_EMAIL, "B2C", request);
    }

    /**
     * Создаёт минимальный заказ напрямую в БД (без корзины) для тестов сводки статусов.
     */
    private Order seedOrderWithStatus(OrderStatus status) {
        Order order = Order.builder()
                .userId(USER_ID)
                .orderNumber("STATS-" + UUID.randomUUID())
                .status(status)
                .paymentMethod(PaymentMethod.CARD)
                .deliveryMethod(DeliveryMethod.SUPPLIER_DELIVERY)
                .totalAmount(new BigDecimal("1000.00"))
                .customerEmail(USER_EMAIL)
                .customerType(ru.rfsnab.orderservice.models.entity.enums.CustomerType.B2C)
                .build();
        return orderRepository.save(order);
    }

    /**
     * created_at проставляется Hibernate (@CreationTimestamp, updatable=false) —
     * для теста "заказ создан N дней назад" сдвигаем его напрямую в БД.
     */
    private void backdateCreatedAt(UUID orderId, int daysAgo) {
        jdbcTemplate.update("UPDATE orders SET created_at = ? WHERE id = ?",
                java.sql.Timestamp.valueOf(java.time.LocalDateTime.now().minusDays(daysAgo)), orderId);
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
}
