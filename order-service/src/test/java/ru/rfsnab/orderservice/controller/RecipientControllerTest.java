package ru.rfsnab.orderservice.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import ru.rfsnab.orderservice.service.RecipientService;

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
 * Тесты контроллера получателей.
 */
@AutoConfigureMockMvc
@DisplayName("RecipientController")
class RecipientControllerTest extends BaseIntegrationTest {

    @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private RecipientService recipientService;

    private static final Long USER_ID = 100L;
    private static final String USER_EMAIL = "user@email.com";

    private Recipient buildRecipient(Long id, String name, boolean isDefault) {
        Recipient recipient = new Recipient();
        recipient.setId(id);
        recipient.setUserId(USER_ID);
        recipient.setName(name);
        recipient.setPhone("+79001234567");
        recipient.setDefault(isDefault);
        recipient.setCreatedAt(LocalDateTime.now());
        return recipient;
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
    @DisplayName("GET /api/v1/recipients — список получателей")
    class GetAllTests {

        @Test
        @DisplayName("200 OK — возвращает список получателей")
        void shouldReturnRecipients() throws Exception {
            when(recipientService.getByUserId(USER_ID))
                    .thenReturn(List.of(buildRecipient(1L, "Иванов Иван", true)));

            mockMvc.perform(get("/api/v1/recipients").with(jwtUser()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].name").value("Иванов Иван"))
                    .andExpect(jsonPath("$[0].isDefault").value(true));
        }

        @Test
        @DisplayName("200 OK — пустой список")
        void shouldReturnEmptyList() throws Exception {
            when(recipientService.getByUserId(USER_ID)).thenReturn(List.of());

            mockMvc.perform(get("/api/v1/recipients").with(jwtUser()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isEmpty());
        }
    }

    @Nested
    @DisplayName("POST /api/v1/recipients — создание получателя")
    class CreateTests {

        @Test
        @DisplayName("201 Created — получатель создан")
        void shouldCreateRecipient() throws Exception {
            Recipient recipient = buildRecipient(1L, "Иванов Иван", false);
            when(recipientService.create(eq(USER_ID), any())).thenReturn(recipient);

            String json = """
                    {
                        "name": "Иванов Иван",
                        "phone": "+79001234567",
                        "isDefault": false
                    }
                    """;

            mockMvc.perform(post("/api/v1/recipients")
                            .with(jwtUser()).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.name").value("Иванов Иван"))
                    .andExpect(jsonPath("$.phone").value("+79001234567"));

            verify(recipientService).create(eq(USER_ID), any());
        }

        @Test
        @DisplayName("400 Bad Request — имя обязательно")
        void shouldReturn400WhenNameBlank() throws Exception {
            String json = """
                    {
                        "name": "",
                        "phone": "+79001234567",
                        "isDefault": false
                    }
                    """;

            mockMvc.perform(post("/api/v1/recipients")
                            .with(jwtUser()).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("400 Bad Request — телефон невалидный")
        void shouldReturn400WhenPhoneInvalid() throws Exception {
            String json = """
                    {
                        "name": "Иванов Иван",
                        "phone": "123",
                        "isDefault": false
                    }
                    """;

            mockMvc.perform(post("/api/v1/recipients")
                            .with(jwtUser()).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("PUT /api/v1/recipients/{id} — обновление получателя")
    class UpdateTests {

        @Test
        @DisplayName("200 OK — получатель обновлён")
        void shouldUpdateRecipient() throws Exception {
            Recipient updated = buildRecipient(1L, "Новое имя", false);
            when(recipientService.update(eq(1L), eq(USER_ID), any())).thenReturn(updated);

            String json = """
                    {
                        "name": "Новое имя",
                        "phone": "+79001234567",
                        "isDefault": false
                    }
                    """;

            mockMvc.perform(put("/api/v1/recipients/1")
                            .with(jwtUser()).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.name").value("Новое имя"));
        }
    }

    @Nested
    @DisplayName("DELETE /api/v1/recipients/{id} — удаление получателя")
    class DeleteTests {

        @Test
        @DisplayName("204 No Content — получатель удалён")
        void shouldDeleteRecipient() throws Exception {
            mockMvc.perform(delete("/api/v1/recipients/1")
                            .with(jwtUser()).with(csrf()))
                    .andExpect(status().isNoContent());

            verify(recipientService).delete(1L, USER_ID);
        }
    }

    @Nested
    @DisplayName("PATCH /api/v1/recipients/{id}/default — установка по умолчанию")
    class SetDefaultTests {

        @Test
        @DisplayName("200 OK — получатель установлен по умолчанию")
        void shouldSetDefault() throws Exception {
            Recipient recipient = buildRecipient(1L, "Иванов Иван", true);
            when(recipientService.setDefault(1L, USER_ID)).thenReturn(recipient);

            mockMvc.perform(patch("/api/v1/recipients/1/default")
                            .with(jwtUser()).with(csrf()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.isDefault").value(true));
        }
    }

    @Nested
    @DisplayName("401 Unauthorized — без токена")
    class UnauthorizedTests {

        @Test
        @DisplayName("401 при запросе без токена")
        void shouldReturn401WithoutToken() throws Exception {
            mockMvc.perform(get("/api/v1/recipients"))
                    .andExpect(status().isUnauthorized());
        }
    }
}