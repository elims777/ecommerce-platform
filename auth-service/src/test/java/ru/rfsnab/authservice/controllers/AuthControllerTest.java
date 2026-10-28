package ru.rfsnab.authservice.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import ru.rfsnab.authservice.exceptions.AuthServiceException;
import ru.rfsnab.authservice.exceptions.InvalidOAuth2CodeException;
import ru.rfsnab.authservice.exceptions.UserNotFoundException;
import ru.rfsnab.authservice.models.dto.AuthResponse;
import ru.rfsnab.authservice.models.dto.RoleEntity;
import ru.rfsnab.authservice.models.dto.SimpleAuthRequest;
import ru.rfsnab.authservice.models.dto.SwitchContextRequest;
import ru.rfsnab.authservice.models.dto.UserDtoResponse;
import ru.rfsnab.authservice.service.AuthService;
import ru.rfsnab.authservice.service.OAuth2CodeService;

import java.util.Map;
import java.util.Set;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration тесты для AuthController
 * Тестируем REST API endpoints с моками сервисов
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("AuthController Integration Tests")
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private OAuth2CodeService oAuth2CodeService;

    private SimpleAuthRequest validAuthRequest;
    private UserDtoResponse validUserResponse;
    private Map<String, Object> authDataWithUser;

    @BeforeEach
    void setUp() {
        // Подготовка тестовых данных
        validAuthRequest = new SimpleAuthRequest();
        validAuthRequest.setEmail("test@example.com");
        validAuthRequest.setPassword("password123");

        RoleEntity userRole = new RoleEntity();
        userRole.setId(1L);
        userRole.setName("USER");

        validUserResponse = new UserDtoResponse();
        validUserResponse.setId(1L);
        validUserResponse.setEmail("test@example.com");
        validUserResponse.setFirstname("Test");
        validUserResponse.setLastname("User");
        validUserResponse.setRoles(Set.of(userRole));

        authDataWithUser = Map.of(
                "access_token", "access.token.here",
                "refresh_token", "refresh.token.here",
                "token_type", "Bearer",
                "expires_in", 3600,
                "user", validUserResponse
        );
    }

    // ==================== POST /v1/auth/login (JSON) Tests ====================

    @Test
    @DisplayName("POST /v1/auth/login - успешный login возвращает токены и user data")
    void loginJson_Success_ReturnsAuthDataWithUser() throws Exception {
        // Given
        when(authService.authenticateWithUserData(any(SimpleAuthRequest.class)))
                .thenReturn(authDataWithUser);

        // When & Then
        mockMvc.perform(post("/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validAuthRequest)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.access_token").value("access.token.here"))
                .andExpect(jsonPath("$.refresh_token").value("refresh.token.here"))
                .andExpect(jsonPath("$.token_type").value("Bearer"))
                .andExpect(jsonPath("$.expires_in").value(3600))
                .andExpect(jsonPath("$.user.email").value("test@example.com"))
                .andExpect(jsonPath("$.user.id").value(1));

        verify(authService).authenticateWithUserData(any(SimpleAuthRequest.class));
    }

    @Test
    @DisplayName("POST /v1/auth/login - битый JSON → 400, а не 500")
    void loginJson_MalformedJson_Returns400() throws Exception {
        mockMvc.perform(post("/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not-json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    @DisplayName("GET несуществующего пути → 404, а не 500")
    void unknownPath_Returns404() throws Exception {
        mockMvc.perform(get("/v1/auth/no-such-endpoint"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    @DisplayName("POST /v1/auth/login - невалидный email → 400 Bad Request")
    void loginJson_InvalidEmail_Returns400() throws Exception {
        // Given
        SimpleAuthRequest invalidRequest = new SimpleAuthRequest();
        invalidRequest.setEmail("not-an-email");
        invalidRequest.setPassword("password123");

        // When & Then
        mockMvc.perform(post("/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest());

        verify(authService, never()).authenticateWithUserData(any());
    }

    @Test
    @DisplayName("POST /v1/auth/login - пустой email → 400 Bad Request")
    void loginJson_EmptyEmail_Returns400() throws Exception {
        // Given
        SimpleAuthRequest invalidRequest = new SimpleAuthRequest();
        invalidRequest.setEmail("");
        invalidRequest.setPassword("password123");

        // When & Then
        mockMvc.perform(post("/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest());

        verify(authService, never()).authenticateWithUserData(any());
    }

    @Test
    @DisplayName("POST /v1/auth/login - пустой password → 400 Bad Request")
    void loginJson_EmptyPassword_Returns400() throws Exception {
        // Given
        SimpleAuthRequest invalidRequest = new SimpleAuthRequest();
        invalidRequest.setEmail("test@example.com");
        invalidRequest.setPassword("");

        // When & Then
        mockMvc.perform(post("/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest());

        verify(authService, never()).authenticateWithUserData(any());
    }

    // ==================== POST /v1/auth/refresh Tests ====================

    @Test
    @DisplayName("POST /v1/auth/refresh - валидный refresh token возвращает новые токены")
    void refresh_ValidToken_ReturnsNewTokens() throws Exception {
        // Given
        String refreshToken = "valid.refresh.token";
        AuthResponse newTokens = AuthResponse.builder()
                .accessToken("new.access.token")
                .refreshToken("new.refresh.token")
                .tokenType("Bearer")
                .clientType("B2C")
                .build();

        when(authService.refresh(refreshToken)).thenReturn(newTokens);

        // When & Then
        mockMvc.perform(post("/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refreshToken + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("new.access.token"))
                .andExpect(jsonPath("$.refreshToken").value("new.refresh.token"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"));

        verify(authService).refresh(refreshToken);
    }

    @Test
    @DisplayName("POST /v1/auth/refresh - пустой refreshToken в теле → 400")
    void refresh_BlankToken_Returns400() throws Exception {
        // When & Then
        mockMvc.perform(post("/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"\"}"))
                .andExpect(status().isBadRequest());

        verify(authService, never()).refresh(anyString());
    }

    // ==================== POST /v1/auth/switch-context Tests ====================

    @Test
    @DisplayName("POST /v1/auth/switch-context - отказ в переключении → 403, а не 500")
    void switchContext_AccessDenied_Returns403() throws Exception {
        when(authService.switchContext(anyString(), any()))
                .thenThrow(new AccessDeniedException("Нет подтверждённой связи с юрлицом"));

        mockMvc.perform(post("/v1/auth/switch-context")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer some.token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SwitchContextRequest("B2B"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Нет подтверждённой связи с юрлицом"));
    }

    @Test
    @DisplayName("POST /v1/auth/switch-context - без Authorization header → 401, а не 500")
    void switchContext_MissingAuthHeader_Returns401() throws Exception {
        mockMvc.perform(post("/v1/auth/switch-context")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SwitchContextRequest("B2B"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Unauthorized"));

        verify(authService, never()).switchContext(anyString(), any());
    }

    @Test
    @DisplayName("POST /v1/auth/switch-context - заголовок без Bearer → 401")
    void switchContext_NonBearerHeader_Returns401() throws Exception {
        mockMvc.perform(post("/v1/auth/switch-context")
                        .header(HttpHeaders.AUTHORIZATION, "Basic dGVzdDp0ZXN0")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SwitchContextRequest("B2B"))))
                .andExpect(status().isUnauthorized());

        verify(authService, never()).switchContext(anyString(), any());
    }

    // ==================== POST /v1/auth/oauth2/exchange Tests ====================

    @Test
    @DisplayName("POST /v1/auth/oauth2/exchange - валидный код → 200 и тот же JSON с токенами")
    void exchangeOAuth2Code_ValidCode_ReturnsTokens() throws Exception {
        when(oAuth2CodeService.exchange("good-code")).thenReturn(authDataWithUser);

        mockMvc.perform(post("/v1/auth/oauth2/exchange")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"good-code\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access_token").value("access.token.here"))
                .andExpect(jsonPath("$.refresh_token").value("refresh.token.here"))
                .andExpect(jsonPath("$.user.email").value("test@example.com"));
    }

    @Test
    @DisplayName("POST /v1/auth/oauth2/exchange - неизвестный/использованный/истёкший код → 400")
    void exchangeOAuth2Code_InvalidCode_Returns400() throws Exception {
        when(oAuth2CodeService.exchange("bad-code")).thenThrow(new InvalidOAuth2CodeException("Код недействителен или истёк"));

        mockMvc.perform(post("/v1/auth/oauth2/exchange")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"bad-code\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Код недействителен или истёк"));
    }

    @Test
    @DisplayName("POST /v1/auth/oauth2/exchange - пустой code → 400, сервис не вызывается")
    void exchangeOAuth2Code_BlankCode_Returns400() throws Exception {
        mockMvc.perform(post("/v1/auth/oauth2/exchange")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"\"}"))
                .andExpect(status().isBadRequest());

        verify(oAuth2CodeService, never()).exchange(any());
    }

    // ==================== Ошибки уровня HTTP ====================

    @Test
    @DisplayName("GET на POST-only endpoint → 405, а не 500")
    void wrongMethod_Returns405() throws Exception {
        mockMvc.perform(get("/v1/auth/logout"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.status").value(405));
    }

    // ==================== GET /v1/auth/me Tests ====================

    @Test
    @DisplayName("GET /v1/auth/me - валидный Bearer token возвращает user data")
    void getCurrentUser_ValidBearerToken_ReturnsUserData() throws Exception {
        when(authService.getCurrentUser("valid.access.token")).thenReturn(validUserResponse);

        mockMvc.perform(get("/v1/auth/me")
                        .header("Authorization", "Bearer valid.access.token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("test@example.com"))
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.firstname").value("Test"))
                .andExpect(jsonPath("$.lastname").value("User"));
    }

    @Test
    @DisplayName("GET /v1/auth/me - отсутствует Authorization header → 401")
    void getCurrentUser_MissingAuthHeader_Returns401() throws Exception {
        mockMvc.perform(get("/v1/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Unauthorized"));

        verify(authService, never()).getCurrentUser(anyString());
    }

    @Test
    @DisplayName("GET /v1/auth/me - невалидный формат токена (не Bearer) → 401")
    void getCurrentUser_InvalidTokenFormat_Returns401() throws Exception {
        mockMvc.perform(get("/v1/auth/me")
                        .header("Authorization", "Basic dGVzdDp0ZXN0"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Unauthorized"));

        verify(authService, never()).getCurrentUser(anyString());
    }

    @Test
    @DisplayName("GET /v1/auth/me - сервис бросает JwtException → 401")
    void getCurrentUser_JwtException_Returns401() throws Exception {
        when(authService.getCurrentUser(anyString())).thenThrow(new JwtException("Токен недействителен или истек"));

        mockMvc.perform(get("/v1/auth/me")
                        .header("Authorization", "Bearer invalid.token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Unauthorized"));
    }

    @Test
    @DisplayName("GET /v1/auth/me - сервис бросает UserNotFoundException → 404")
    void getCurrentUser_UserNotFound_Returns404() throws Exception {
        when(authService.getCurrentUser(anyString())).thenThrow(new UserNotFoundException("Пользователь не найден"));

        mockMvc.perform(get("/v1/auth/me")
                        .header("Authorization", "Bearer valid.token"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("Пользователь не найден"));
    }

    @Test
    @DisplayName("GET /v1/auth/me - сервис бросает AuthServiceException → 502")
    void getCurrentUser_AuthServiceException_Returns502() throws Exception {
        when(authService.getCurrentUser(anyString())).thenThrow(new AuthServiceException("Ошибка получения данных пользователя"));

        mockMvc.perform(get("/v1/auth/me")
                        .header("Authorization", "Bearer valid.token"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.status").value(502))
                .andExpect(jsonPath("$.message").value("Ошибка получения данных пользователя"));
    }

    // ==================== POST /v1/auth/logout Tests ====================

    @Test
    @DisplayName("POST /v1/auth/logout - успешный logout")
    void logout_Success_ReturnsSuccessMessage() throws Exception {
        // When & Then
        mockMvc.perform(post("/v1/auth/logout"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Logout successful"))
                .andExpect(jsonPath("$.status").value("success"));
    }
}