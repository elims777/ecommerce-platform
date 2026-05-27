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
import ru.rfsnab.orderservice.models.entity.Recipient;
import ru.rfsnab.orderservice.models.entity.RecipientAddress;
import ru.rfsnab.orderservice.service.RecipientAddressService;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Тесты контроллера адресов получателей.
 */
@AutoConfigureMockMvc
@DisplayName("RecipientAddressController")
class RecipientAddressControllerTest extends BaseIntegrationTest {

    @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RecipientAddressService addressService;

    private static final Long USER_ID = 100L;
    private static final Long RECIPIENT_ID = 1L;
    private static final String USER_EMAIL = "user@email.com";

    private RecipientAddress buildAddress(Long id, String label, boolean isDefault) {
        Recipient recipient = new Recipient();
        recipient.setId(RECIPIENT_ID);

        RecipientAddress address = new RecipientAddress();
        address.setId(id);
        address.setRecipient(recipient);
        address.setLabel(label);
        address.setCity("Москва");
        address.setStreet("Ленина");
        address.setBuilding("1");
        address.setApartment("10");
        address.setPostalCode("101000");
        address.setDefault(isDefault);
        address.setCreatedAt(LocalDateTime.now());
        return address;
    }

    private static RequestPostProcessor jwtUser() {
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(
                        USER_ID.toString(), null,
                        List.of(new SimpleGrantedAuthority("ROLE_USER")));
        auth.setDetails(Map.of("email", USER_EMAIL));
        return SecurityMockMvcRequestPostProcessors.authentication(auth);
    }

    @Nested
    @DisplayName("GET /api/v1/recipients/{recipientId}/addresses")
    class GetAllTests {

        @Test
        @DisplayName("200 OK — возвращает список адресов")
        void shouldReturnAddresses() throws Exception {
            when(addressService.getByRecipientId(RECIPIENT_ID, USER_ID))
                    .thenReturn(List.of(buildAddress(1L, "Офис", true)));

            mockMvc.perform(get("/api/v1/recipients/{recipientId}/addresses", RECIPIENT_ID)
                            .with(jwtUser()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].label").value("Офис"))
                    .andExpect(jsonPath("$[0].city").value("Москва"));
        }

        @Test
        @DisplayName("200 OK — пустой список")
        void shouldReturnEmptyList() throws Exception {
            when(addressService.getByRecipientId(RECIPIENT_ID, USER_ID)).thenReturn(List.of());

            mockMvc.perform(get("/api/v1/recipients/{recipientId}/addresses", RECIPIENT_ID)
                            .with(jwtUser()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isEmpty());
        }
    }

    @Nested
    @DisplayName("POST /api/v1/recipients/{recipientId}/addresses")
    class CreateTests {

        @Test
        @DisplayName("201 Created — адрес создан")
        void shouldCreateAddress() throws Exception {
            RecipientAddress address = buildAddress(1L, "Офис", false);
            when(addressService.create(eq(RECIPIENT_ID), eq(USER_ID), any())).thenReturn(address);

            String json = """
                    {
                        "label": "Офис",
                        "city": "Москва",
                        "street": "Ленина",
                        "building": "1",
                        "apartment": "10",
                        "postalCode": "101000",
                        "isDefault": false
                    }
                    """;

            mockMvc.perform(post("/api/v1/recipients/{recipientId}/addresses", RECIPIENT_ID)
                            .with(jwtUser()).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.label").value("Офис"))
                    .andExpect(jsonPath("$.city").value("Москва"));

            verify(addressService).create(eq(RECIPIENT_ID), eq(USER_ID), any());
        }

        @Test
        @DisplayName("400 Bad Request — город обязателен")
        void shouldReturn400WhenCityBlank() throws Exception {
            String json = """
                    {
                        "label": "Офис",
                        "city": "",
                        "street": "Ленина",
                        "building": "1",
                        "isDefault": false
                    }
                    """;

            mockMvc.perform(post("/api/v1/recipients/{recipientId}/addresses", RECIPIENT_ID)
                            .with(jwtUser()).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("PUT /api/v1/recipients/{recipientId}/addresses/{id}")
    class UpdateTests {

        @Test
        @DisplayName("200 OK — адрес обновлён")
        void shouldUpdateAddress() throws Exception {
            RecipientAddress updated = buildAddress(1L, "Новый офис", false);
            when(addressService.update(eq(1L), eq(RECIPIENT_ID), eq(USER_ID), any())).thenReturn(updated);

            String json = """
                    {
                        "label": "Новый офис",
                        "city": "Москва",
                        "street": "Ленина",
                        "building": "1",
                        "isDefault": false
                    }
                    """;

            mockMvc.perform(put("/api/v1/recipients/{recipientId}/addresses/1", RECIPIENT_ID)
                            .with(jwtUser()).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.label").value("Новый офис"));
        }
    }

    @Nested
    @DisplayName("DELETE /api/v1/recipients/{recipientId}/addresses/{id}")
    class DeleteTests {

        @Test
        @DisplayName("204 No Content — адрес удалён")
        void shouldDeleteAddress() throws Exception {
            mockMvc.perform(delete("/api/v1/recipients/{recipientId}/addresses/1", RECIPIENT_ID)
                            .with(jwtUser()).with(csrf()))
                    .andExpect(status().isNoContent());

            verify(addressService).delete(1L, RECIPIENT_ID, USER_ID);
        }
    }

    @Nested
    @DisplayName("PATCH /api/v1/recipients/{recipientId}/addresses/{id}/default")
    class SetDefaultTests {

        @Test
        @DisplayName("200 OK — адрес установлен по умолчанию")
        void shouldSetDefault() throws Exception {
            RecipientAddress address = buildAddress(1L, "Офис", true);
            when(addressService.setDefault(1L, RECIPIENT_ID, USER_ID)).thenReturn(address);

            mockMvc.perform(patch("/api/v1/recipients/{recipientId}/addresses/1/default", RECIPIENT_ID)
                            .with(jwtUser()).with(csrf()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.isDefault").value(true));
        }
    }
}