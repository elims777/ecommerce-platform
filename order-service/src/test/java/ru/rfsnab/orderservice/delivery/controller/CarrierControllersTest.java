package ru.rfsnab.orderservice.delivery.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import ru.rfsnab.orderservice.BaseIntegrationTest;
import ru.rfsnab.orderservice.delivery.models.dto.CarrierDto;
import ru.rfsnab.orderservice.delivery.models.dto.CarrierSyncReport;
import ru.rfsnab.orderservice.delivery.models.dto.CarrierTerminalDto;
import ru.rfsnab.orderservice.delivery.models.entity.enums.CarrierCode;
import ru.rfsnab.orderservice.delivery.models.entity.enums.PointType;
import ru.rfsnab.orderservice.delivery.models.entity.enums.SyncStatus;
import ru.rfsnab.orderservice.delivery.service.CarrierService;
import ru.rfsnab.orderservice.delivery.service.CarrierSyncService;
import ru.rfsnab.orderservice.delivery.service.DeliveryService;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@DisplayName("DeliveryController + AdminCarrierController")
class CarrierControllersTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CarrierService carrierService;

    @MockitoBean
    private DeliveryService deliveryService;

    @MockitoBean
    private CarrierSyncService carrierSyncService;

    private static CarrierDto dellin(boolean enabled, int sortOrder) {
        return new CarrierDto(CarrierCode.DELLIN, "Деловые Линии", enabled, true, true, sortOrder,
                true, Instant.parse("2026-10-12T20:00:00Z"), 120, null, null, false);
    }

    private static final CarrierTerminalDto TERMINAL = new CarrierTerminalDto(7L, CarrierCode.DELLIN,
            PointType.TERMINAL, "Новосибирск", "Терминал Толмачёво", "ул. Ленина, 1", 55.03, 82.92, "Пн-Пт 9-18", null);

    private static RequestPostProcessor auth(String role) {
        UsernamePasswordAuthenticationToken token = new UsernamePasswordAuthenticationToken(
                "1", null, List.of(new SimpleGrantedAuthority(role)));
        token.setDetails(Map.of("email", "u@rfsnab.ru"));
        return SecurityMockMvcRequestPostProcessors.authentication(token);
    }

    @Test
    @DisplayName("GET /delivery/carriers — включённые ТК с названием и флагами")
    void clientGetsEnabledCarriers() throws Exception {
        when(carrierService.getEnabled()).thenReturn(List.of(dellin(true, 2)));

        mockMvc.perform(get("/api/v1/delivery/carriers").with(auth("ROLE_USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].code").value("DELLIN"))
                .andExpect(jsonPath("$[0].name").value("Деловые Линии"))
                .andExpect(jsonPath("$[0].addressDeliveryEnabled").value(true))
                .andExpect(jsonPath("$[0].terminalDeliveryEnabled").value(true));
    }

    @Test
    @DisplayName("GET /delivery/carriers — 401 без авторизации")
    void clientUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/delivery/carriers"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /delivery/cities — 200 для USER, строка массивом")
    void clientGetsCities() throws Exception {
        when(deliveryService.findCities(CarrierCode.DELLIN, PointType.TERMINAL, "новос"))
                .thenReturn(List.of("Новосибирск"));

        mockMvc.perform(get("/api/v1/delivery/cities").with(auth("ROLE_USER"))
                        .param("carrier", "DELLIN").param("type", "TERMINAL").param("q", "новос"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0]").value("Новосибирск"));
    }

    @Test
    @DisplayName("GET /delivery/cities — 401 без авторизации")
    void citiesUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/delivery/cities")
                        .param("carrier", "DELLIN").param("type", "TERMINAL").param("q", "новос"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(deliveryService);
    }

    @Test
    @DisplayName("GET /delivery/cities — 400, а не 500, без обязательного параметра")
    void citiesMissingParameter() throws Exception {
        mockMvc.perform(get("/api/v1/delivery/cities").with(auth("ROLE_USER"))
                        .param("carrier", "DELLIN").param("type", "TERMINAL"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Missing Parameter"));
    }

    @Test
    @DisplayName("GET /delivery/terminals — 200 для USER, поля точки по контракту")
    void clientGetsTerminals() throws Exception {
        when(deliveryService.getTerminals(CarrierCode.DELLIN, "Новосибирск", PointType.TERMINAL))
                .thenReturn(List.of(TERMINAL));

        mockMvc.perform(get("/api/v1/delivery/terminals").with(auth("ROLE_USER"))
                        .param("carrier", "DELLIN").param("city", "Новосибирск").param("type", "TERMINAL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(7))
                .andExpect(jsonPath("$[0].carrierCode").value("DELLIN"))
                .andExpect(jsonPath("$[0].pointType").value("TERMINAL"))
                .andExpect(jsonPath("$[0].latitude").value(55.03))
                .andExpect(jsonPath("$[0].workingHours").value("Пн-Пт 9-18"))
                .andExpect(jsonPath("$[0].phone").isEmpty());
    }

    @Test
    @DisplayName("GET /delivery/terminals — 400 на неизвестный тип точки")
    void terminalsUnknownType() throws Exception {
        mockMvc.perform(get("/api/v1/delivery/terminals").with(auth("ROLE_USER"))
                        .param("carrier", "DELLIN").param("city", "Новосибирск").param("type", "KIOSK"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Invalid Parameter"));
    }

    @Test
    @DisplayName("GET /delivery/terminals — 401 без авторизации")
    void terminalsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/delivery/terminals")
                        .param("carrier", "DELLIN").param("city", "Новосибирск").param("type", "TERMINAL"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /admin/carriers — ADMIN видит все")
    void adminGetsAll() throws Exception {
        when(carrierService.getAll()).thenReturn(List.of(dellin(true, 2)));

        mockMvc.perform(get("/api/v1/admin/carriers").with(auth("ROLE_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].code").value("DELLIN"));
    }

    @Test
    @DisplayName("GET /admin/carriers — 403 для USER")
    void userForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/admin/carriers").with(auth("ROLE_USER")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /admin/carriers — 401 без авторизации")
    void adminUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/admin/carriers"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("PATCH /admin/carriers/{code} — обновляет флаги без terminalDeliveryEnabled")
    void adminUpdates() throws Exception {
        when(carrierService.update(eq(CarrierCode.DELLIN), any())).thenReturn(dellin(false, 7));

        mockMvc.perform(patch("/api/v1/admin/carriers/DELLIN").with(auth("ROLE_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":false,\"addressDeliveryEnabled\":true,\"sortOrder\":7}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false))
                .andExpect(jsonPath("$.sortOrder").value(7));
    }

    @Test
    @DisplayName("PATCH /admin/carriers/{code} — принимает terminalDeliveryEnabled")
    void adminUpdatesTerminalFlag() throws Exception {
        when(carrierService.update(eq(CarrierCode.DELLIN), any())).thenReturn(dellin(true, 2));

        mockMvc.perform(patch("/api/v1/admin/carriers/DELLIN").with(auth("ROLE_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":true,\"addressDeliveryEnabled\":true,\"sortOrder\":2,\"terminalDeliveryEnabled\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.terminalDeliveryEnabled").value(true));
    }

    @Test
    @DisplayName("PATCH /admin/carriers/{code} — шаблон ссылки: без http(s) или без {number} — 400, пустой и верный — 200")
    void adminUpdateTrackingTemplateValidation() throws Exception {
        when(carrierService.update(eq(CarrierCode.DELLIN), any())).thenReturn(dellin(true, 2));
        String head = "{\"enabled\":true,\"addressDeliveryEnabled\":true,\"sortOrder\":2,\"trackingUrlTemplate\":\"";
        for (String bad : new String[]{"ftp://x/{number}", "javascript:alert({number})", "https://x/track"}) {
            mockMvc.perform(patch("/api/v1/admin/carriers/DELLIN").with(auth("ROLE_ADMIN"))
                            .contentType(MediaType.APPLICATION_JSON).content(head + bad + "\"}"))
                    .andExpect(status().isBadRequest());
        }
        for (String ok : new String[]{"", "https://x/track/{number}/"}) {
            mockMvc.perform(patch("/api/v1/admin/carriers/DELLIN").with(auth("ROLE_ADMIN"))
                            .contentType(MediaType.APPLICATION_JSON).content(head + ok + "\"}"))
                    .andExpect(status().isOk());
        }
    }

    @Test
    @DisplayName("PATCH /admin/carriers/{code} — 400 без обязательных полей")
    void adminUpdateValidation() throws Exception {
        mockMvc.perform(patch("/api/v1/admin/carriers/DELLIN").with(auth("ROLE_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("PATCH /admin/carriers/{code} — 400, а не 500, на лишнее поле в теле")
    void adminUpdateUnknownField() throws Exception {
        mockMvc.perform(patch("/api/v1/admin/carriers/DELLIN").with(auth("ROLE_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"x\",\"enabled\":true,\"addressDeliveryEnabled\":true,\"sortOrder\":2}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Malformed Request"));
    }

    @Test
    @DisplayName("PATCH /admin/carriers/{code} — 400, а не 500, на неизвестный код ТК")
    void adminUpdateUnknownCode() throws Exception {
        mockMvc.perform(patch("/api/v1/admin/carriers/FOO").with(auth("ROLE_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":true,\"addressDeliveryEnabled\":true,\"sortOrder\":2}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Invalid Parameter"));
    }

    @Test
    @DisplayName("PATCH /admin/carriers/{code} — 403 для USER")
    void userCannotUpdate() throws Exception {
        mockMvc.perform(patch("/api/v1/admin/carriers/DELLIN").with(auth("ROLE_USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":false,\"addressDeliveryEnabled\":true,\"sortOrder\":7}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /admin/carriers/{code}/sync-terminals — ADMIN получает отчёт")
    void adminSyncs() throws Exception {
        CarrierSyncReport report = new CarrierSyncReport(CarrierCode.DELLIN, "Деловые Линии", SyncStatus.WARNING,
                10, 2, 1, 0, 1, List.of(new CarrierSyncReport.SkippedItem("X1", "Нет города")), 0,
                null, 1500, Instant.parse("2026-10-12T20:00:05Z"));
        when(carrierSyncService.syncManually(CarrierCode.DELLIN)).thenReturn(report);

        mockMvc.perform(post("/api/v1/admin/carriers/DELLIN/sync-terminals").with(auth("ROLE_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.carrierCode").value("DELLIN"))
                .andExpect(jsonPath("$.status").value("WARNING"))
                .andExpect(jsonPath("$.received").value(10))
                .andExpect(jsonPath("$.skippedItems[0].externalId").value("X1"))
                .andExpect(jsonPath("$.skippedItems[0].reason").value("Нет города"))
                .andExpect(jsonPath("$.moreSkipped").value(0))
                .andExpect(jsonPath("$.finishedAt").value("2026-10-12T20:00:05Z"));
    }

    @Test
    @DisplayName("POST /admin/carriers/{code}/sync-terminals — 403 для USER")
    void userCannotSync() throws Exception {
        mockMvc.perform(post("/api/v1/admin/carriers/DELLIN/sync-terminals").with(auth("ROLE_USER")))
                .andExpect(status().isForbidden());
        verifyNoInteractions(carrierSyncService);
    }

    @Test
    @DisplayName("POST /admin/carriers/{code}/sync-terminals — 401 без авторизации")
    void syncUnauthorized() throws Exception {
        mockMvc.perform(post("/api/v1/admin/carriers/DELLIN/sync-terminals"))
                .andExpect(status().isUnauthorized());
    }
}
