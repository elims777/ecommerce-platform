package ru.rfsnab.notificationservice.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import ru.rfsnab.notificationservice.models.PasswordResetToken;
import ru.rfsnab.notificationservice.service.EmailService;
import ru.rfsnab.notificationservice.service.PasswordResetTokenService;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PasswordResetController.class)
@DisplayName("PasswordResetController Web MVC Tests")
class PasswordResetControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private PasswordResetTokenService tokenService;

    @MockitoBean
    private EmailService emailService;

    @Nested
    @DisplayName("POST /api/password-reset/create")
    class CreateEndpointTests {

        @Test
        @DisplayName("валидный запрос → 200 и вызваны tokenService.create + emailService.sendPasswordResetEmail")
        void create_ValidRequest_ReturnsOkAndInvokesServices() throws Exception {
            var request = new PasswordResetController.CreateResetRequest(
                    1L, "USER", "test@example.com", "Иван", "raw-token-123");

            mockMvc.perform(post("/api/password-reset/create")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk());

            verify(tokenService).create(1L, "USER", "test@example.com", "raw-token-123");
            verify(emailService).sendPasswordResetEmail("test@example.com", "Иван", "raw-token-123");
        }
    }

    @Nested
    @DisplayName("POST /api/password-reset/consume")
    class ConsumeEndpointTests {

        @Test
        @DisplayName("валидный токен → valid=true с accountId и accountType")
        void consume_ValidToken_ReturnsValidTrue() throws Exception {
            PasswordResetToken token = PasswordResetToken.builder()
                    .id(1L)
                    .accountId(42L)
                    .accountType("USER")
                    .email("test@example.com")
                    .createdAt(LocalDateTime.now())
                    .expiresAt(LocalDateTime.now().plusHours(1))
                    .used(true)
                    .build();
            when(tokenService.consume("raw-token-123")).thenReturn(Optional.of(token));

            mockMvc.perform(post("/api/password-reset/consume")
                            .param("token", "raw-token-123"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.valid", is(true)))
                    .andExpect(jsonPath("$.accountId", is(42)))
                    .andExpect(jsonPath("$.accountType", is("USER")));
        }

        @Test
        @DisplayName("невалидный токен → valid=false")
        void consume_InvalidToken_ReturnsValidFalse() throws Exception {
            when(tokenService.consume("garbage-token")).thenReturn(Optional.empty());

            mockMvc.perform(post("/api/password-reset/consume")
                            .param("token", "garbage-token"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.valid", is(false)))
                    .andExpect(jsonPath("$.accountId", nullValue()))
                    .andExpect(jsonPath("$.accountType", nullValue()));
        }
    }

    @Nested
    @DisplayName("GET /api/password-reset/validate")
    class ValidateEndpointTests {

        @Test
        @DisplayName("валидный токен → valid=true")
        void validate_ValidToken_ReturnsValidTrue() throws Exception {
            when(tokenService.isValid("raw-token-123")).thenReturn(true);

            mockMvc.perform(get("/api/password-reset/validate")
                            .param("token", "raw-token-123"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.valid", is(true)));
        }

        @Test
        @DisplayName("невалидный токен → valid=false")
        void validate_InvalidToken_ReturnsValidFalse() throws Exception {
            when(tokenService.isValid("garbage-token")).thenReturn(false);

            mockMvc.perform(get("/api/password-reset/validate")
                            .param("token", "garbage-token"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.valid", is(false)));
        }
    }
}
