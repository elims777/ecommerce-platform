package ru.rfsnab.orderservice.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import ru.rfsnab.orderservice.BaseIntegrationTest;
import ru.rfsnab.orderservice.models.dto.order.CreateOrderRequest;
import ru.rfsnab.orderservice.models.dto.order.UpdateOrderRequest;
import ru.rfsnab.orderservice.models.entity.DeliveryAddress;
import ru.rfsnab.orderservice.models.entity.Order;
import ru.rfsnab.orderservice.models.entity.OrderItem;
import ru.rfsnab.orderservice.models.entity.WarehousePoint;
import ru.rfsnab.orderservice.models.entity.enums.DeliveryMethod;
import ru.rfsnab.orderservice.models.entity.enums.OrderStatus;
import ru.rfsnab.orderservice.models.entity.enums.PaymentMethod;
import ru.rfsnab.orderservice.service.CartService;
import ru.rfsnab.orderservice.service.OrderService;
import ru.rfsnab.orderservice.service.WarehousePointService;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Тесты контроллера заказов.
 * Полный контекст через BaseIntegrationTest + AutoConfigureMockMvc.
 * OrderService и WarehousePointService замоканы — тестируем HTTP слой.
 * .with(user("100")) — эмуляция JWT authentication.
 * Raw JSON для request body — избегаем проблему с @JsonFormat(OBJECT) при сериализации enum.
 */
@AutoConfigureMockMvc
@DisplayName("OrderController")
class OrderControllerTest extends BaseIntegrationTest {

    @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OrderService orderService;

    @MockitoBean
    private CartService cartService;

    @MockitoBean
    private WarehousePointService warehousePointService;

    private static final Long USER_ID = 100L;
    private static final UUID ORDER_ID = UUID.randomUUID();

    // ==================== POST /api/v1/orders ====================

    @Nested
    @DisplayName("POST /api/v1/orders — создание заказа")
    class CreateOrderTests {

        @Test
        @DisplayName("201 Created — заказ успешно создан (DELIVERY)")
        void shouldCreateDeliveryOrder() throws Exception {
            Order order = buildDeliveryOrder();
            when(orderService.createOrder(eq(USER_ID), any(CreateOrderRequest.class)))
                    .thenReturn(order);

            String json = """
                    {
                        "paymentMethod": "CARD",
                        "deliveryMethod": "SUPPLIER_DELIVERY",
                        "deliveryAddress": {
                            "city": "Сыктывкар",
                            "street": "Октябрьский проспект",
                            "building": "1",
                            "phone": "+79001234567",
                            "recipientName": "Иванов Иван"
                        },
                        "comment": "Тестовый комментарий"
                    }
                    """;

            mockMvc.perform(post("/api/v1/orders")
                            .with(user("100")).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.orderNumber").value("ABC-00001"))
                    .andExpect(jsonPath("$.status.code").value("CREATED"))
                    .andExpect(jsonPath("$.deliveryAddress.city").value("Сыктывкар"));

            verify(orderService).createOrder(eq(USER_ID), any(CreateOrderRequest.class));
        }

        @Test
        @DisplayName("201 Created — заказ с самовывозом (PICKUP)")
        void shouldCreatePickupOrder() throws Exception {
            Order order = buildPickupOrder();
            WarehousePoint point = buildWarehousePoint();
            when(orderService.createOrder(eq(USER_ID), any(CreateOrderRequest.class)))
                    .thenReturn(order);
            when(warehousePointService.getActivePoint(1L)).thenReturn(point);

            String json = """
                    {
                        "paymentMethod": "CASH_ON_DELIVERY",
                        "deliveryMethod": "PICKUP",
                        "warehousePointId": 1
                    }
                    """;

            mockMvc.perform(post("/api/v1/orders")
                            .with(user("100")).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.warehousePoint.name").value("Склад РФСнаб"));
        }

        @Test
        @DisplayName("400 Bad Request — paymentMethod отсутствует")
        void shouldReturn400WhenPaymentMethodNull() throws Exception {
            String json = """
                    {
                        "deliveryMethod": "PICKUP",
                        "warehousePointId": 1
                    }
                    """;

            mockMvc.perform(post("/api/v1/orders")
                            .with(user("100")).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json))
                    .andExpect(status().isBadRequest());
        }
    }

    // ==================== GET /api/v1/orders ====================

    @Nested
    @DisplayName("GET /api/v1/orders — список заказов")
    class GetUserOrdersTests {

        @Test
        @DisplayName("200 OK — возвращает страницу с заказами")
        void shouldReturnPageOfOrders() throws Exception {
            Order order = buildDeliveryOrder();
            Page<Order> page = new PageImpl<>(List.of(order));
            when(orderService.getUserOrders(eq(USER_ID), any(Pageable.class)))
                    .thenReturn(page);

            mockMvc.perform(get("/api/v1/orders")
                            .with(user("100")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(1)))
                    .andExpect(jsonPath("$.content[0].orderNumber").value("ABC-00001"))
                    .andExpect(jsonPath("$.content[0].status.code").value("CREATED"));
        }

        @Test
        @DisplayName("200 OK — пустой список")
        void shouldReturnEmptyPage() throws Exception {
            when(orderService.getUserOrders(eq(USER_ID), any(Pageable.class)))
                    .thenReturn(Page.empty());

            mockMvc.perform(get("/api/v1/orders")
                            .with(user("100")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(0)));
        }
    }

    // ==================== GET /api/v1/orders/{id} ====================

    @Nested
    @DisplayName("GET /api/v1/orders/{id} — получение заказа")
    class GetOrderTests {

        @Test
        @DisplayName("200 OK — возвращает заказ с items")
        void shouldReturnOrder() throws Exception {
            Order order = buildDeliveryOrder();
            when(orderService.getOrderByIdAndUser(order.getId(), USER_ID))
                    .thenReturn(order);

            mockMvc.perform(get("/api/v1/orders/{id}", order.getId())
                            .with(user("100")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.orderNumber").value("ABC-00001"))
                    .andExpect(jsonPath("$.items", hasSize(1)))
                    .andExpect(jsonPath("$.items[0].productName").value("Доска обрезная"));
        }
    }

    // ==================== PUT /api/v1/orders/{id} ====================

    @Nested
    @DisplayName("PUT /api/v1/orders/{id} — обновление заказа")
    class UpdateOrderTests {

        @Test
        @DisplayName("200 OK — заказ обновлён")
        void shouldUpdateOrder() throws Exception {
            Order updatedOrder = buildDeliveryOrder();
            updatedOrder.setPaymentMethod(PaymentMethod.SBP);
            when(orderService.updateOrder(eq(ORDER_ID), eq(USER_ID),
                    any(UpdateOrderRequest.class))).thenReturn(updatedOrder);

            String json = """
                    {
                        "paymentMethod": "SBP",
                        "deliveryMethod": "SUPPLIER_DELIVERY",
                        "deliveryAddress": {
                            "city": "Сыктывкар",
                            "street": "Октябрьский проспект",
                            "building": "1",
                            "phone": "+79001234567",
                            "recipientName": "Иванов Иван"
                        },
                        "items": [
                            {"productId": 1, "productName": "Доска", "quantity": 20, "price": 1500.00, "subtotal": 30000.00}
                        ],
                        "comment": "Обновлённый комментарий"
                    }
                    """;

            mockMvc.perform(put("/api/v1/orders/{id}", ORDER_ID)
                            .with(user("100")).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.paymentMethod.code").value("SBP"));
        }

        @Test
        @DisplayName("400 Bad Request — items пустой")
        void shouldReturn400WhenItemsEmpty() throws Exception {
            String json = """
                    {
                        "paymentMethod": "CARD",
                        "deliveryMethod": "PICKUP",
                        "warehousePointId": 1,
                        "items": []
                    }
                    """;

            mockMvc.perform(put("/api/v1/orders/{id}", ORDER_ID)
                            .with(user("100")).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json))
                    .andExpect(status().isBadRequest());
        }
    }

    // ==================== POST /api/v1/orders/{id}/cancel ====================

    @Nested
    @DisplayName("POST /api/v1/orders/{id}/cancel — отмена заказа")
    class CancelOrderTests {

        @Test
        @DisplayName("200 OK — заказ отменён")
        void shouldCancelOrder() throws Exception {
            Order cancelledOrder = buildDeliveryOrder();
            cancelledOrder.setStatus(OrderStatus.CANCELLED);
            when(orderService.cancelOrder(ORDER_ID, USER_ID)).thenReturn(cancelledOrder);

            mockMvc.perform(post("/api/v1/orders/{id}/cancel", ORDER_ID)
                            .with(user("100")).with(csrf()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status.code").value("CANCELLED"));
        }
    }

    // ==================== POST /api/v1/orders/{id}/pay ====================

    @Nested
    @DisplayName("POST /api/v1/orders/{id}/pay — инициация оплаты")
    class InitiatePaymentTests {

        @Test
        @DisplayName("200 OK — оплата инициирована")
        void shouldInitiatePayment() throws Exception {
            Order order = buildDeliveryOrder();
            order.setStatus(OrderStatus.PENDING_PAYMENT);
            when(orderService.initiatePayment(ORDER_ID, USER_ID)).thenReturn(order);

            mockMvc.perform(post("/api/v1/orders/{id}/pay", ORDER_ID)
                            .with(user("100")).with(csrf()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status.code").value("PENDING_PAYMENT"));
        }
    }

    // ==================== POST /api/v1/orders/{id}/repeat ====================

    @Nested
    @DisplayName("POST /api/v1/orders/{id}/repeat — повторный заказ")
    class RepeatOrderTests {

        @Test
        @DisplayName("201 Created — повторный заказ создан")
        void shouldRepeatOrder() throws Exception {
            Order newOrder = buildDeliveryOrder();
            newOrder.setOrderNumber("ABC-00002");
            when(orderService.repeatOrder(ORDER_ID, USER_ID)).thenReturn(newOrder);

            mockMvc.perform(post("/api/v1/orders/{id}/repeat", ORDER_ID)
                            .with(user("100")).with(csrf()))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.orderNumber").value("ABC-00002"))
                    .andExpect(jsonPath("$.status.code").value("CREATED"));
        }
    }

    // ==================== Test data builders ====================

    private Order buildDeliveryOrder() {
        Order order = Order.builder()
                .id(ORDER_ID)
                .userId(USER_ID)
                .orderNumber("ABC-00001")
                .status(OrderStatus.CREATED)
                .paymentMethod(PaymentMethod.CARD)
                .deliveryMethod(DeliveryMethod.SUPPLIER_DELIVERY)
                .totalAmount(new BigDecimal("15000.00"))
                .deliveryAddress(DeliveryAddress.builder()
                        .city("Сыктывкар").street("Октябрьский проспект")
                        .building("1").phone("+79001234567")
                        .recipientName("Иванов Иван").build())
                .trackingNumber("TRACK-123")
                .comment("Тестовый заказ")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        OrderItem item = OrderItem.builder()
                .productId(1L).productName("Доска обрезная")
                .quantity(10).price(new BigDecimal("1500.00"))
                .order(order).build();
        order.setItems(new ArrayList<>(List.of(item)));

        return order;
    }

    private Order buildPickupOrder() {
        return Order.builder()
                .id(ORDER_ID)
                .userId(USER_ID)
                .orderNumber("ABC-00002")
                .status(OrderStatus.CREATED)
                .paymentMethod(PaymentMethod.CASH_ON_DELIVERY)
                .deliveryMethod(DeliveryMethod.PICKUP)
                .totalAmount(new BigDecimal("5000.00"))
                .warehousePointId(1L)
                .items(new ArrayList<>())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    private WarehousePoint buildWarehousePoint() {
        return WarehousePoint.builder()
                .id(1L).name("Склад РФСнаб")
                .city("Сыктывкар").street("Октябрьский проспект")
                .building("1").postalCode("167000")
                .phoneNumber("+7 (8212) 00-00-00")
                .workingHours("Пн-Пт: 9:00-18:00")
                .active(true).build();
    }
}