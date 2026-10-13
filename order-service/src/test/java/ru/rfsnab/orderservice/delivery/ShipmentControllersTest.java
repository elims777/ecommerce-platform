package ru.rfsnab.orderservice.delivery;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.data.domain.PageRequest;
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
import ru.rfsnab.orderservice.models.entity.enums.DeliveryMethod;
import ru.rfsnab.orderservice.models.entity.enums.OrderStatus;
import ru.rfsnab.orderservice.models.entity.enums.PaymentMethod;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@DisplayName("Отправления: AdminShipmentController и /admin/orders/{id}/shipments")
class ShipmentControllersTest extends BaseIntegrationTest {

    private static final UUID ORDER_ID = UUID.randomUUID();

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ShipmentService shipmentService;
    @MockitoBean
    private ShipmentJournalImporter journalImporter;

    private static ShipmentDto dto(long id) {
        return new ShipmentDto(id, ORDER_ID, "RF-00001", CarrierCode.DELLIN, "Деловые Линии", "12345",
                ShipmentDirection.OUTGOING, "ООО Мы", "ИП Клиент", "Москва", "Омск", null, null,
                "В пути", ShipmentStatusCode.IN_TRANSIT, Instant.parse("2026-10-12T10:00:00Z"), null,
                false, false, ShipmentSource.MANUAL, "https://www.dellin.ru/tracker/orders/12345/",
                Instant.parse("2026-10-12T09:00:00Z"));
    }

    private static RequestPostProcessor auth(String role) {
        UsernamePasswordAuthenticationToken token = new UsernamePasswordAuthenticationToken(
                "1", null, List.of(new SimpleGrantedAuthority(role)));
        token.setDetails(Map.of("email", "u@rfsnab.ru"));
        return SecurityMockMvcRequestPostProcessors.authentication(token);
    }

    private static Order order() {
        Order order = Order.builder().id(ORDER_ID).userId(42L).orderNumber("RF-00001").status(OrderStatus.SHIPPED)
                .paymentMethod(PaymentMethod.CARD).deliveryMethod(DeliveryMethod.CARRIER_ADDRESS)
                .totalAmount(new BigDecimal("100.00")).customerEmail("c@c.ru")
                .shipmentMode(ShipmentMode.MULTIPLE).shipmentsComplete(true).build();
        order.setItems(new ArrayList<>());
        return order;
    }

    @Nested
    @DisplayName("GET /admin/shipments")
    class JournalEndpoint {

        @Test
        @DisplayName("401 без токена, 403 для USER — сервис не вызывается")
        void security() throws Exception {
            mockMvc.perform(get("/api/v1/admin/shipments")).andExpect(status().isUnauthorized());
            mockMvc.perform(get("/api/v1/admin/shipments").with(auth("ROLE_USER"))).andExpect(status().isForbidden());

            verifyNoInteractions(shipmentService);
        }

        @Test
        @DisplayName("200 для ADMIN: фильтры разбираются (ISO-момент из toISOString), страница по контракту")
        void adminGetsPage() throws Exception {
            when(shipmentService.getJournal(any(), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(dto(1)), PageRequest.of(0, 20), 1));

            mockMvc.perform(get("/api/v1/admin/shipments").with(auth("ROLE_ADMIN"))
                            .param("carrier", "DELLIN").param("direction", "OUTGOING").param("statusCode", "PROBLEM")
                            .param("city", "Омск").param("counterparty", "Ромашка").param("search", "RF-1").param("from", "2026-10-12T00:00:00.000Z")
                            .param("to", "2026-10-13T00:00:00.000Z").param("problem", "true").param("stale", "true")
                            .param("page", "0").param("size", "20"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].id").value(1))
                    .andExpect(jsonPath("$.content[0].carrierName").value("Деловые Линии"))
                    .andExpect(jsonPath("$.content[0].orderNumber").value("RF-00001"))
                    .andExpect(jsonPath("$.content[0].carrierStatusCode").value("IN_TRANSIT"))
                    .andExpect(jsonPath("$.content[0].trackingUrl").value("https://www.dellin.ru/tracker/orders/12345/"))
                    .andExpect(jsonPath("$.content[0].pollingStopped").value(false))
                    .andExpect(jsonPath("$.content[0].notFound").value(false))
                    .andExpect(jsonPath("$.totalElements").value(1));

            ArgumentCaptor<ShipmentFilter> captor = ArgumentCaptor.forClass(ShipmentFilter.class);
            verify(shipmentService).getJournal(captor.capture(), any(Pageable.class));
            ShipmentFilter filter = captor.getValue();
            assertThat(filter.carrier()).isEqualTo(CarrierCode.DELLIN);
            assertThat(filter.direction()).isEqualTo(ShipmentDirection.OUTGOING);
            assertThat(filter.statusCode()).isEqualTo(ShipmentStatusCode.PROBLEM);
            assertThat(filter.city()).isEqualTo("Омск");
            assertThat(filter.counterparty()).isEqualTo("Ромашка");
            assertThat(filter.search()).isEqualTo("RF-1");
            assertThat(filter.from()).isEqualTo(Instant.parse("2026-10-12T00:00:00Z"));
            assertThat(filter.to()).isEqualTo(Instant.parse("2026-10-13T00:00:00Z"));
            assertThat(filter.problem()).isTrue();
            assertThat(filter.stale()).isTrue();
            assertThat(filter.notFound()).isFalse();
            assertThat(filter.awaitingMore()).isFalse();
        }
    }

    @Nested
    @DisplayName("POST /admin/shipments/import")
    class ImportEndpoint {

        private static final String URL = "/api/v1/admin/shipments/import";

        @Test
        @DisplayName("401 / 403 — импорт не запускается")
        void security() throws Exception {
            mockMvc.perform(post(URL)).andExpect(status().isUnauthorized());
            mockMvc.perform(post(URL).with(auth("ROLE_USER"))).andExpect(status().isForbidden());

            verifyNoInteractions(journalImporter);
        }

        @Test
        @DisplayName("ADMIN: 202, если импорт запущен; 409, если уже идёт")
        void startedOrRunning() throws Exception {
            when(journalImporter.importAsync()).thenReturn(true, false);

            mockMvc.perform(post(URL).with(auth("ROLE_ADMIN"))).andExpect(status().isAccepted());
            mockMvc.perform(post(URL).with(auth("ROLE_ADMIN"))).andExpect(status().isConflict());
        }
    }

    @Nested
    @DisplayName("POST /admin/shipments/{id}/refresh и /link")
    class RefreshAndLink {

        @Test
        @DisplayName("401 / 403 / 200 на refresh")
        void refresh() throws Exception {
            when(shipmentService.refresh(5L)).thenReturn(dto(5));

            mockMvc.perform(post("/api/v1/admin/shipments/5/refresh")).andExpect(status().isUnauthorized());
            mockMvc.perform(post("/api/v1/admin/shipments/5/refresh").with(auth("ROLE_USER"))).andExpect(status().isForbidden());
            mockMvc.perform(post("/api/v1/admin/shipments/5/refresh").with(auth("ROLE_ADMIN")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(5));
        }

        @Test
        @DisplayName("ТК недоступна при refresh — 502, а не 500")
        void refreshCarrierDown() throws Exception {
            when(shipmentService.refresh(6L)).thenThrow(new CarrierApiException("ТК недоступна"));

            mockMvc.perform(post("/api/v1/admin/shipments/6/refresh").with(auth("ROLE_ADMIN")))
                    .andExpect(status().isBadGateway());
        }

        @Test
        @DisplayName("401 / 403 / 200 на link; пустой номер заказа — 400")
        void link() throws Exception {
            when(shipmentService.link(5L, "RF-00001")).thenReturn(dto(5));
            String body = "{\"orderNumber\":\"RF-00001\"}";

            mockMvc.perform(post("/api/v1/admin/shipments/5/link").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isUnauthorized());
            mockMvc.perform(post("/api/v1/admin/shipments/5/link").with(auth("ROLE_USER"))
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isForbidden());
            mockMvc.perform(post("/api/v1/admin/shipments/5/link").with(auth("ROLE_ADMIN"))
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.orderNumber").value("RF-00001"));
            mockMvc.perform(post("/api/v1/admin/shipments/5/link").with(auth("ROLE_ADMIN"))
                            .contentType(MediaType.APPLICATION_JSON).content("{\"orderNumber\":\"\"}"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("отправления нет — 404")
        void notFound() throws Exception {
            when(shipmentService.refresh(404L)).thenThrow(new ShipmentNotFoundException("Отправление не найдено: 404"));

            mockMvc.perform(post("/api/v1/admin/shipments/404/refresh").with(auth("ROLE_ADMIN")))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("/admin/orders/{id}/shipments")
    class OrderShipments {

        private static final String BASE = "/api/v1/admin/orders/" + "00000000-0000-0000-0000-000000000001" + "/shipments";
        private static final String BODY = "{\"carrierCode\":\"DELLIN\",\"waybillNumber\":\"12345\",\"shipmentMode\":\"SINGLE\"}";

        @Test
        @DisplayName("POST: 401 / 403 / 201; created_by берётся из токена")
        void add() throws Exception {
            when(shipmentService.add(any(UUID.class), any(AddShipmentRequest.class), eq(1L))).thenReturn(dto(9));

            mockMvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content(BODY))
                    .andExpect(status().isUnauthorized());
            mockMvc.perform(post(BASE).with(auth("ROLE_USER")).contentType(MediaType.APPLICATION_JSON).content(BODY))
                    .andExpect(status().isForbidden());
            mockMvc.perform(post(BASE).with(auth("ROLE_ADMIN")).contentType(MediaType.APPLICATION_JSON).content(BODY))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").value(9))
                    .andExpect(jsonPath("$.waybillNumber").value("12345"));

            ArgumentCaptor<AddShipmentRequest> captor = ArgumentCaptor.forClass(AddShipmentRequest.class);
            verify(shipmentService).add(any(UUID.class), captor.capture(), eq(1L));
            assertThat(captor.getValue().shipmentMode()).isEqualTo(ShipmentMode.SINGLE);
        }

        @Test
        @DisplayName("POST: пустая накладная или нет ТК — 400")
        void addValidation() throws Exception {
            mockMvc.perform(post(BASE).with(auth("ROLE_ADMIN")).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"carrierCode\":\"DELLIN\",\"waybillNumber\":\" \"}"))
                    .andExpect(status().isBadRequest());
            mockMvc.perform(post(BASE).with(auth("ROLE_ADMIN")).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"waybillNumber\":\"1\"}"))
                    .andExpect(status().isBadRequest());
            verifyNoInteractions(shipmentService);
        }

        @Test
        @DisplayName("POST: вторая накладная при SINGLE — 409 с SHIPMENT_MODE_SWITCH_REQUIRED в error, code и message")
        void addSwitchRequired() throws Exception {
            when(shipmentService.add(any(UUID.class), any(AddShipmentRequest.class), any()))
                    .thenThrow(new ShipmentModeSwitchRequiredException());

            mockMvc.perform(post(BASE).with(auth("ROLE_ADMIN")).contentType(MediaType.APPLICATION_JSON).content(BODY))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error").value("SHIPMENT_MODE_SWITCH_REQUIRED"))
                    .andExpect(jsonPath("$.code").value("SHIPMENT_MODE_SWITCH_REQUIRED"))
                    .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("SHIPMENT_MODE_SWITCH_REQUIRED")));
        }

        @Test
        @DisplayName("POST: накладной нет в ТК — 400 с сообщением; ТК недоступна — 502")
        void addCarrierCheckFailed() throws Exception {
            when(shipmentService.add(any(UUID.class), any(AddShipmentRequest.class), any()))
                    .thenThrow(new WaybillNotFoundInCarrierException("Накладная 12345 не найдена в Деловые Линии"))
                    .thenThrow(new CarrierApiException("ТК недоступна"));

            mockMvc.perform(post(BASE).with(auth("ROLE_ADMIN")).contentType(MediaType.APPLICATION_JSON).content(BODY))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Накладная 12345 не найдена в Деловые Линии"));
            mockMvc.perform(post(BASE).with(auth("ROLE_ADMIN")).contentType(MediaType.APPLICATION_JSON).content(BODY))
                    .andExpect(status().isBadGateway());
        }

        @Test
        @DisplayName("POST: дубль накладной — 409 без кода режима")
        void addDuplicate() throws Exception {
            when(shipmentService.add(any(UUID.class), any(AddShipmentRequest.class), any()))
                    .thenThrow(new ShipmentAlreadyExistsException("Накладная 12345 уже есть в журнале"));

            mockMvc.perform(post(BASE).with(auth("ROLE_ADMIN")).contentType(MediaType.APPLICATION_JSON).content(BODY))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").doesNotExist());
        }

        @Test
        @DisplayName("POST unlink: 401 / 403 / 204")
        void unlinkShipment() throws Exception {
            mockMvc.perform(post(BASE + "/9/unlink")).andExpect(status().isUnauthorized());
            mockMvc.perform(post(BASE + "/9/unlink").with(auth("ROLE_USER"))).andExpect(status().isForbidden());
            mockMvc.perform(post(BASE + "/9/unlink").with(auth("ROLE_ADMIN"))).andExpect(status().isNoContent());

            verify(shipmentService).unlink(any(UUID.class), eq(9L));
        }

        @Test
        @DisplayName("POST complete: 401 / 403 / 200 с OrderDto (режим, флаг, отправления)")
        void complete() throws Exception {
            when(shipmentService.complete(any(UUID.class))).thenReturn(order());
            when(shipmentService.getByOrderId(ORDER_ID)).thenReturn(List.of(dto(9)));

            mockMvc.perform(post(BASE + "/complete")).andExpect(status().isUnauthorized());
            mockMvc.perform(post(BASE + "/complete").with(auth("ROLE_USER"))).andExpect(status().isForbidden());
            mockMvc.perform(post(BASE + "/complete").with(auth("ROLE_ADMIN")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.orderNumber").value("RF-00001"))
                    .andExpect(jsonPath("$.shipmentMode").value("MULTIPLE"))
                    .andExpect(jsonPath("$.shipmentsComplete").value(true))
                    .andExpect(jsonPath("$.shipments[0].waybillNumber").value("12345"));
        }
    }
}
