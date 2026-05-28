package ru.rfsnab.orderservice.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import ru.rfsnab.orderservice.BaseIntegrationTest;
import ru.rfsnab.orderservice.models.entity.Order;
import ru.rfsnab.orderservice.models.entity.OrderItem;
import ru.rfsnab.orderservice.models.entity.enums.DeliveryMethod;
import ru.rfsnab.orderservice.models.entity.enums.OrderStatus;
import ru.rfsnab.orderservice.models.entity.enums.PaymentMethod;
import ru.rfsnab.orderservice.service.OrderService;
import ru.rfsnab.orderservice.service.WarehousePointService;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@DisplayName("AdminOrderController")
class AdminOrderControllerTest extends BaseIntegrationTest {

    @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OrderService orderService;

    @MockitoBean
    private WarehousePointService warehousePointService;

    private static final UUID ORDER_ID = UUID.randomUUID();
    private static final String ADMIN_EMAIL = "admin@rfsnab.ru";

    // ==================== GET /api/v1/admin/orders ====================

    @Nested
    @DisplayName("GET /api/v1/admin/orders — список всех заявок")
    class GetAdminOrdersTests {

        @Test
        @DisplayName("200 OK — возвращает страницу заявок для ADMIN")
        void shouldReturnOrdersForAdmin() throws Exception {
            Order order = buildOrder();
            when(orderService.getAdminOrders(any(), any(), any(), any(), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of(order)));

            mockMvc.perform(get("/api/v1/admin/orders")
                            .with(jwtAdmin()).with(csrf()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isArray())
                    .andExpect(jsonPath("$.content[0].orderNumber").value("RF-00001"))
                    .andExpect(jsonPath("$.content[0].status.code").value("CREATED"));

            verify(orderService).getAdminOrders(any(), any(), any(), any(), any(Pageable.class));
        }

        @Test
        @DisplayName("401 Unauthorized — без токена")
        void shouldReturn401WithoutToken() throws Exception {
            mockMvc.perform(get("/api/v1/admin/orders"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("403 Forbidden — пользователь без роли ADMIN")
        void shouldReturn403ForRegularUser() throws Exception {
            mockMvc.perform(get("/api/v1/admin/orders")
                            .with(jwtUser()).with(csrf()))
                    .andExpect(status().isForbidden());
        }
    }

    // ==================== GET /api/v1/admin/orders/{id} ====================

    @Nested
    @DisplayName("GET /api/v1/admin/orders/{id} — детали заявки")
    class GetAdminOrderTests {

        @Test
        @DisplayName("200 OK — возвращает заявку по ID для ADMIN")
        void shouldReturnOrderById() throws Exception {
            Order order = buildOrder();
            when(orderService.getOrder(ORDER_ID)).thenReturn(order);

            mockMvc.perform(get("/api/v1/admin/orders/{id}", ORDER_ID)
                            .with(jwtAdmin()).with(csrf()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.orderNumber").value("RF-00001"))
                    .andExpect(jsonPath("$.status.code").value("CREATED"));
        }

        @Test
        @DisplayName("403 Forbidden — обычный пользователь не может получить чужой заказ")
        void shouldReturn403ForRegularUser() throws Exception {
            mockMvc.perform(get("/api/v1/admin/orders/{id}", ORDER_ID)
                            .with(jwtUser()).with(csrf()))
                    .andExpect(status().isForbidden());
        }
    }

    // ==================== PATCH /api/v1/admin/orders/{id}/status ====================

    @Nested
    @DisplayName("PATCH /api/v1/admin/orders/{id}/status — смена статуса")
    class ChangeStatusTests {

        @Test
        @DisplayName("200 OK — статус успешно изменён")
        void shouldChangeStatus() throws Exception {
            Order order = buildOrder();
            order.setStatus(OrderStatus.PROCESSING);
            when(orderService.updateStatus(eq(ORDER_ID), eq(OrderStatus.PROCESSING))).thenReturn(order);

            mockMvc.perform(patch("/api/v1/admin/orders/{id}/status", ORDER_ID)
                            .with(jwtAdmin()).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"status\": \"PROCESSING\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status.code").value("PROCESSING"));

            verify(orderService).updateStatus(ORDER_ID, OrderStatus.PROCESSING);
        }

        @Test
        @DisplayName("400 Bad Request — пустой status")
        void shouldReturn400WhenStatusBlank() throws Exception {
            mockMvc.perform(patch("/api/v1/admin/orders/{id}/status", ORDER_ID)
                            .with(jwtAdmin()).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"status\": \"\"}"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("400 Bad Request — неизвестный статус")
        void shouldReturn400WhenStatusUnknown() throws Exception {
            mockMvc.perform(patch("/api/v1/admin/orders/{id}/status", ORDER_ID)
                            .with(jwtAdmin()).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"status\": \"UNKNOWN_STATUS\"}"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("403 Forbidden — пользователь без роли ADMIN")
        void shouldReturn403ForRegularUser() throws Exception {
            mockMvc.perform(patch("/api/v1/admin/orders/{id}/status", ORDER_ID)
                            .with(jwtUser()).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"status\": \"PROCESSING\"}"))
                    .andExpect(status().isForbidden());
        }
    }

    // ==================== Test data builders ====================

    private Order buildOrder() {
        Order order = Order.builder()
                .id(ORDER_ID)
                .userId(42L)
                .orderNumber("RF-00001")
                .status(OrderStatus.CREATED)
                .paymentMethod(PaymentMethod.CARD)
                .deliveryMethod(DeliveryMethod.SUPPLIER_DELIVERY)
                .totalAmount(new BigDecimal("12000.00"))
                .customerEmail("client@company.ru")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        OrderItem item = OrderItem.builder()
                .productId(1L).productName("Труба стальная")
                .quantity(5).price(new BigDecimal("2400.00"))
                .order(order).build();
        order.setItems(new ArrayList<>(List.of(item)));
        return order;
    }

    private static RequestPostProcessor jwtAdmin() {
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(
                        "1", null,
                        List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        auth.setDetails(Map.of("email", ADMIN_EMAIL));
        return SecurityMockMvcRequestPostProcessors.authentication(auth);
    }

    private static RequestPostProcessor jwtUser() {
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(
                        "99", null,
                        List.of(new SimpleGrantedAuthority("ROLE_USER")));
        auth.setDetails(Map.of("email", "user@company.ru"));
        return SecurityMockMvcRequestPostProcessors.authentication(auth);
    }
}
