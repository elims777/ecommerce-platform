package ru.rfsnab.authservice.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import ru.rfsnab.authservice.exceptions.InvalidResetTokenException;
import ru.rfsnab.authservice.models.dto.ForgotPasswordRequest;
import ru.rfsnab.authservice.models.dto.ResetPasswordRequest;
import ru.rfsnab.authservice.service.PasswordResetService;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("PasswordResetController Integration Tests")
class PasswordResetControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private PasswordResetService passwordResetService;

    @Test
    @DisplayName("POST /api/v1/auth/forgot-password - валидный email → 200")
    void forgotPassword_ValidEmail_Returns200() throws Exception {
        doNothing().when(passwordResetService).forgot("test@example.com");

        mockMvc.perform(post("/api/v1/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ForgotPasswordRequest("test@example.com"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").exists());

        verify(passwordResetService).forgot("test@example.com");
    }

    @Test
    @DisplayName("POST /api/v1/auth/forgot-password - невалидный email → 400")
    void forgotPassword_InvalidEmail_Returns400() throws Exception {
        mockMvc.perform(post("/api/v1/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ForgotPasswordRequest("not-an-email"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/v1/auth/reset-password - valid токен → 200")
    void resetPassword_ValidToken_Returns200() throws Exception {
        doNothing().when(passwordResetService).reset("valid-token", "newPassword123");

        mockMvc.perform(post("/api/v1/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new ResetPasswordRequest("valid-token", "newPassword123"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").exists());

        verify(passwordResetService).reset("valid-token", "newPassword123");
    }

    @Test
    @DisplayName("POST /api/v1/auth/reset-password - invalid токен → 400")
    void resetPassword_InvalidToken_Returns400() throws Exception {
        doThrow(new InvalidResetTokenException("Ссылка недействительна или устарела"))
                .when(passwordResetService).reset("bad-token", "newPassword123");

        mockMvc.perform(post("/api/v1/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new ResetPasswordRequest("bad-token", "newPassword123"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Ссылка недействительна или устарела"));
    }

    @Test
    @DisplayName("POST /api/v1/auth/reset-password - пароль короче 8 символов → 400")
    void resetPassword_ShortPassword_Returns400() throws Exception {
        mockMvc.perform(post("/api/v1/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new ResetPasswordRequest("valid-token", "short"))))
                .andExpect(status().isBadRequest());

        verify(passwordResetService, org.mockito.Mockito.never()).reset(anyString(), anyString());
    }

    @Test
    @DisplayName("GET /api/v1/auth/reset-password/validate - проксирует флаг valid")
    void validateResetToken_ReturnsValidFlag() throws Exception {
        when(passwordResetService.validate("some-token")).thenReturn(true);

        mockMvc.perform(get("/api/v1/auth/reset-password/validate").param("token", "some-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true));
    }
}
