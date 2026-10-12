package ru.rfsnab.orderservice.delivery;

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

import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@DisplayName("DeliveryController + AdminCarrierController")
class CarrierControllersTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CarrierService carrierService;

    private static final Carrier DELLIN = Carrier.builder()
            .code(CarrierCode.DELLIN).name("Деловые Линии").enabled(true).addressDeliveryEnabled(true).sortOrder(2).build();

    private static RequestPostProcessor auth(String role) {
        UsernamePasswordAuthenticationToken token = new UsernamePasswordAuthenticationToken(
                "1", null, List.of(new SimpleGrantedAuthority(role)));
        token.setDetails(Map.of("email", "u@rfsnab.ru"));
        return SecurityMockMvcRequestPostProcessors.authentication(token);
    }

    @Test
    @DisplayName("GET /delivery/carriers — включённые ТК с названием")
    void clientGetsEnabledCarriers() throws Exception {
        when(carrierService.getEnabled()).thenReturn(List.of(DELLIN));

        mockMvc.perform(get("/api/v1/delivery/carriers").with(auth("ROLE_USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].code").value("DELLIN"))
                .andExpect(jsonPath("$[0].name").value("Деловые Линии"))
                .andExpect(jsonPath("$[0].addressDeliveryEnabled").value(true));
    }

    @Test
    @DisplayName("GET /delivery/carriers — 401 без авторизации")
    void clientUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/delivery/carriers"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /admin/carriers — ADMIN видит все")
    void adminGetsAll() throws Exception {
        when(carrierService.getAll()).thenReturn(List.of(DELLIN));

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
    @DisplayName("PATCH /admin/carriers/{code} — обновляет флаги")
    void adminUpdates() throws Exception {
        Carrier updated = Carrier.builder()
                .code(CarrierCode.DELLIN).name("Деловые Линии").enabled(false).addressDeliveryEnabled(true).sortOrder(7).build();
        when(carrierService.update(eq(CarrierCode.DELLIN), any())).thenReturn(updated);

        mockMvc.perform(patch("/api/v1/admin/carriers/DELLIN").with(auth("ROLE_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":false,\"addressDeliveryEnabled\":true,\"sortOrder\":7}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false))
                .andExpect(jsonPath("$.sortOrder").value(7));
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
}
