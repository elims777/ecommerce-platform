package ru.rfsnab.orderservice.controller;

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
import ru.rfsnab.orderservice.exception.DuplicateWarehousePointException;
import ru.rfsnab.orderservice.models.entity.WarehousePoint;
import ru.rfsnab.orderservice.service.WarehousePointService;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@DisplayName("AdminWarehousePointController")
class AdminWarehousePointControllerTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private WarehousePointService warehousePointService;

    private static final String VALID_BODY = """
            {"name":"Склад Новосибирск","city":"Новосибирск","street":"Ленина","building":"1",
             "postalCode":"630000","phone":"+73830000000","workingHours":"Пн-Пт 9-18",
             "description":null,"latitude":55.030204,"longitude":82.920430,"active":true}
            """;

    private static final WarehousePoint POINT = WarehousePoint.builder()
            .id(5L).name("Склад Новосибирск").city("Новосибирск").street("Ленина").building("1")
            .postalCode("630000").phoneNumber("+73830000000").workingHours("Пн-Пт 9-18")
            .latitude(new BigDecimal("55.030204")).longitude(new BigDecimal("82.920430"))
            .active(true).build();

    private static RequestPostProcessor auth(String role) {
        UsernamePasswordAuthenticationToken token = new UsernamePasswordAuthenticationToken(
                "1", null, List.of(new SimpleGrantedAuthority(role)));
        token.setDetails(Map.of("email", "u@rfsnab.ru"));
        return SecurityMockMvcRequestPostProcessors.authentication(token);
    }

    @Test
    @DisplayName("GET — ADMIN получает все точки, включая неактивные")
    void adminGetsAll() throws Exception {
        WarehousePoint inactive = WarehousePoint.builder().id(6L).name("Старый склад").city("Сыктывкар")
                .street("Мира").building("2").postalCode("167000").phoneNumber("+78212000000")
                .active(false).build();
        when(warehousePointService.getAllPoints()).thenReturn(List.of(POINT, inactive));

        mockMvc.perform(get("/api/v1/admin/warehouse-points").with(auth("ROLE_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].latitude").value(55.030204))
                .andExpect(jsonPath("$[1].active").value(false));
    }

    @Test
    @DisplayName("POST — создаёт точку, 201")
    void adminCreates() throws Exception {
        when(warehousePointService.create(any())).thenReturn(POINT);

        mockMvc.perform(post("/api/v1/admin/warehouse-points").with(auth("ROLE_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(5))
                .andExpect(jsonPath("$.phone").value("+73830000000"));
    }

    @Test
    @DisplayName("POST — 400 без названия")
    void createValidation() throws Exception {
        mockMvc.perform(post("/api/v1/admin/warehouse-points").with(auth("ROLE_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY.replace("\"Склад Новосибирск\"", "\"\"")))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST — 400 при широте вне диапазона")
    void createLatitudeValidation() throws Exception {
        mockMvc.perform(post("/api/v1/admin/warehouse-points").with(auth("ROLE_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY.replace("55.030204", "155.0")))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("PUT — обновляет точку")
    void adminUpdates() throws Exception {
        when(warehousePointService.update(eq(5L), any())).thenReturn(POINT);

        mockMvc.perform(put("/api/v1/admin/warehouse-points/5").with(auth("ROLE_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Склад Новосибирск"));
    }

    @Test
    @DisplayName("POST — дубль названия: 409 с причиной")
    void createDuplicateName() throws Exception {
        when(warehousePointService.create(any()))
                .thenThrow(new DuplicateWarehousePointException("Точка с названием «Склад Новосибирск» уже есть"));

        mockMvc.perform(post("/api/v1/admin/warehouse-points").with(auth("ROLE_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Warehouse Point Name Taken"))
                .andExpect(jsonPath("$.message").value("Точка с названием «Склад Новосибирск» уже есть"));
    }

    @Test
    @DisplayName("POST — без индекса допустимо (postal_code в БД nullable)")
    void createWithoutPostalCode() throws Exception {
        when(warehousePointService.create(any())).thenReturn(POINT);

        mockMvc.perform(post("/api/v1/admin/warehouse-points").with(auth("ROLE_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY.replace("\"630000\"", "null")))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("POST — 403 для USER")
    void userForbidden() throws Exception {
        mockMvc.perform(post("/api/v1/admin/warehouse-points").with(auth("ROLE_USER"))
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET — 403 для USER")
    void userCannotList() throws Exception {
        mockMvc.perform(get("/api/v1/admin/warehouse-points").with(auth("ROLE_USER")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("PUT — 403 для USER")
    void userCannotUpdate() throws Exception {
        mockMvc.perform(put("/api/v1/admin/warehouse-points/5").with(auth("ROLE_USER"))
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET — 401 без авторизации")
    void anonymousUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/admin/warehouse-points"))
                .andExpect(status().isUnauthorized());
    }
}
