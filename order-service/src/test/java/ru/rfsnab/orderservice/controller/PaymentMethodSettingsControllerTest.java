package ru.rfsnab.orderservice.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
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
import ru.rfsnab.orderservice.models.dto.payment.PaymentMethodSettingsDto;
import ru.rfsnab.orderservice.service.PaymentMethodSettingsService;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@DisplayName("PaymentMethodSettingsController")
class PaymentMethodSettingsControllerTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PaymentMethodSettingsService service;

    private static RequestPostProcessor adminAuth() {
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                "1", null,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        auth.setDetails(Map.of("email", "admin@rfsnab.ru"));
        return SecurityMockMvcRequestPostProcessors.authentication(auth);
    }

    private static RequestPostProcessor userAuth() {
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                "99", null,
                List.of(new SimpleGrantedAuthority("ROLE_USER")));
        auth.setDetails(Map.of("email", "user@rfsnab.ru"));
        return SecurityMockMvcRequestPostProcessors.authentication(auth);
    }

    @Nested
    @DisplayName("GET /api/v1/payment-settings")
    class GetSettingsTests {

        @Test
        @DisplayName("200 OK — без авторизации (оба выключены по умолчанию)")
        void shouldReturnSettingsWithoutAuth() throws Exception {
            when(service.getSettings()).thenReturn(new PaymentMethodSettingsDto(false, false));

            mockMvc.perform(get("/api/v1/payment-settings"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.sbpEnabled").value(false))
                    .andExpect(jsonPath("$.cardEnabled").value(false));
        }

        @Test
        @DisplayName("200 OK — sbp включён")
        void shouldReturnSbpEnabled() throws Exception {
            when(service.getSettings()).thenReturn(new PaymentMethodSettingsDto(true, false));

            mockMvc.perform(get("/api/v1/payment-settings"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.sbpEnabled").value(true))
                    .andExpect(jsonPath("$.cardEnabled").value(false));
        }
    }

    @Nested
    @DisplayName("PUT /api/v1/admin/payment-settings")
    class UpdateSettingsTests {

        @Test
        @DisplayName("200 OK — ADMIN может обновить")
        void adminCanUpdate() throws Exception {
            PaymentMethodSettingsDto updated = new PaymentMethodSettingsDto(true, true);
            when(service.updateSettings(any())).thenReturn(updated);

            mockMvc.perform(put("/api/v1/admin/payment-settings")
                            .with(adminAuth())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"sbpEnabled\":true,\"cardEnabled\":true}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.sbpEnabled").value(true))
                    .andExpect(jsonPath("$.cardEnabled").value(true));

            verify(service).updateSettings(any());
        }

        @Test
        @DisplayName("403 — обычный пользователь не может обновить")
        void userCannotUpdate() throws Exception {
            mockMvc.perform(put("/api/v1/admin/payment-settings")
                            .with(userAuth())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"sbpEnabled\":true,\"cardEnabled\":false}"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("401 — без авторизации")
        void unauthenticatedCannotUpdate() throws Exception {
            mockMvc.perform(put("/api/v1/admin/payment-settings")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"sbpEnabled\":true,\"cardEnabled\":false}"))
                    .andExpect(status().isUnauthorized());
        }
    }
}
