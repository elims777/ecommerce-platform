package ru.rfsnab.userservice.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import ru.rfsnab.userservice.exceptions.LegalEntityAlreadyExistsException;
import ru.rfsnab.userservice.exceptions.LegalEntityNotFoundException;
import ru.rfsnab.userservice.models.LegalEntity;
import ru.rfsnab.userservice.models.dto.legal.RegisterLegalEntityRequest;
import ru.rfsnab.userservice.models.enums.VerificationStatus;
import ru.rfsnab.userservice.services.LegalEntityService;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestSecurityConfig.class)
@DisplayName("LegalEntityController")
class LegalEntityControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockitoBean LegalEntityService legalEntityService;

    private static final String REGISTER_JSON = """
            {
                "inn": "1234567890",
                "ogrn": "1234567890123",
                "fullName": "ООО Ромашка",
                "director": "Иванов Иван Иванович",
                "phone": "+79001112233",
                "email": "company@example.com",
                "password": "password123",
                "legalCity": "Сыктывкар",
                "legalStreet": "Октябрьский проспект",
                "legalBuilding": "1",
                "legalPostalCode": "167000"
            }
            """;

    @Nested
    @DisplayName("POST /api/v1/legal-entities/register")
    class RegisterTests {

        @Test
        @DisplayName("201 Created — юрлицо зарегистрировано")
        void shouldRegister() throws Exception {
            LegalEntity entity = LegalEntity.builder()
                    .id(1L).inn("1234567890").ogrn("1234567890123")
                    .fullName("ООО Ромашка").director("Иванов Иван Иванович")
                    .phone("+79001112233").email("company@example.com").password("encoded")
                    .legalCity("Сыктывкар").legalStreet("Октябрьский проспект").legalBuilding("1")
                    .verificationStatus(VerificationStatus.PENDING)
                    .emailVerified(false).createdAt(LocalDateTime.now())
                    .build();
            when(legalEntityService.register(any(RegisterLegalEntityRequest.class))).thenReturn(entity);

            mockMvc.perform(post("/api/v1/legal-entities/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(REGISTER_JSON))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.inn").value("1234567890"))
                    .andExpect(jsonPath("$.verificationStatus").value("PENDING"));
        }

        @Test
        @DisplayName("409 Conflict — ИНН уже существует")
        void shouldReturn409WhenInnExists() throws Exception {
            when(legalEntityService.register(any())).thenThrow(
                    new LegalEntityAlreadyExistsException("ИНН уже зарегистрирован"));

            mockMvc.perform(post("/api/v1/legal-entities/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(REGISTER_JSON))
                    .andExpect(status().isConflict());
        }

        @Test
        @DisplayName("400 Bad Request — невалидный ИНН")
        void shouldReturn400WhenInnInvalid() throws Exception {
            String json = REGISTER_JSON.replace("\"1234567890\"", "\"123\"");
            mockMvc.perform(post("/api/v1/legal-entities/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("GET /api/v1/legal-entities/confirm-email")
    class ConfirmEmailTests {

        @Test
        @DisplayName("200 HTML с meta-redirect → /login?legal_confirmed=true при успехе")
        void shouldConfirmEmail() throws Exception {
            mockMvc.perform(get("/api/v1/legal-entities/confirm-email")
                            .param("token", "valid-token"))
                    .andExpect(status().isOk())
                    .andExpect(content().contentTypeCompatibleWith(org.springframework.http.MediaType.TEXT_HTML))
                    .andExpect(content().string(org.hamcrest.Matchers.containsString("legal_confirmed=true")));
        }

        @Test
        @DisplayName("200 HTML с meta-redirect → /login?legal_confirmed=error при невалидном токене")
        void shouldReturn404WhenTokenInvalid() throws Exception {
            org.mockito.Mockito.doThrow(new LegalEntityNotFoundException("Токен не найден"))
                    .when(legalEntityService).confirmEmail("bad-token");

            mockMvc.perform(get("/api/v1/legal-entities/confirm-email")
                            .param("token", "bad-token"))
                    .andExpect(status().isOk())
                    .andExpect(content().contentTypeCompatibleWith(org.springframework.http.MediaType.TEXT_HTML))
                    .andExpect(content().string(org.hamcrest.Matchers.containsString("legal_confirmed=error")));
        }
    }
}
