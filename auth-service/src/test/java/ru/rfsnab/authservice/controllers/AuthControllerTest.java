package ru.rfsnab.authservice.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import ru.rfsnab.authservice.models.dto.AuthResponse;
import ru.rfsnab.authservice.models.dto.RoleEntity;
import ru.rfsnab.authservice.models.dto.SimpleAuthRequest;
import ru.rfsnab.authservice.models.dto.UserDtoResponse;
import ru.rfsnab.authservice.service.AuthService;
import org.springframework.context.annotation.Import;
import ru.rfsnab.authservice.configuration.SecurityConfig;
import ru.rfsnab.authservice.utils.OAuth2LoginSuccessHandler;

import java.util.Map;
import java.util.Set;

import static org.hamcrest.Matchers.containsString;
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
    private RestTemplate restTemplate;

    private SimpleAuthRequest validAuthRequest;
    private AuthResponse validAuthResponse;
    private UserDtoResponse validUserResponse;
    private Map<String, Object> authDataWithUser;

    @BeforeEach
    void setUp() {
        // Подготовка тестовых данных
        validAuthRequest = new SimpleAuthRequest();
        validAuthRequest.setEmail("test@example.com");
        validAuthRequest.setPassword("password123");

        validAuthResponse = new AuthResponse(
                "access.token.here",
                "refresh.token.here",
                "Bearer"
        );

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
        AuthResponse newTokens = new AuthResponse(
                "new.access.token",
                "new.refresh.token",
                "Bearer"
        );

        when(authService.refresh(refreshToken)).thenReturn(newTokens);

        // When & Then
        mockMvc.perform(post("/v1/auth/refresh")
                        .param("refreshToken", refreshToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("new.access.token"))
                .andExpect(jsonPath("$.refreshToken").value("new.refresh.token"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"));

        verify(authService).refresh(refreshToken);
    }

    @Test
    @DisplayName("POST /v1/auth/refresh - отсутствует refreshToken parameter → 400")
    void refresh_MissingToken_Returns400() throws Exception {
        // When & Then
        mockMvc.perform(post("/v1/auth/refresh"))
                .andExpect(status().isBadRequest());

        verify(authService, never()).refresh(anyString());
    }

    // ==================== GET /v1/auth/me Tests ====================

    @Test
    @DisplayName("GET /v1/auth/me - валидный Bearer token возвращает user data")
    void getCurrentUser_ValidBearerToken_ReturnsUserData() throws Exception {
        // Given
        String accessToken = "valid.access.token";
        String authHeader = "Bearer " + accessToken;

        ResponseEntity<UserDtoResponse> responseEntity = ResponseEntity.ok(validUserResponse);

        when(restTemplate.exchange(
                anyString(),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(UserDtoResponse.class)
        )).thenReturn(responseEntity);

        // When & Then
        mockMvc.perform(get("/v1/auth/me")
                        .header("Authorization", authHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("test@example.com"))
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.firstname").value("Test"))
                .andExpect(jsonPath("$.lastname").value("User"));

        verify(restTemplate).exchange(
                anyString(),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(UserDtoResponse.class)
        );
    }

    @Test
    @DisplayName("GET /v1/auth/me - отсутствует Authorization header → 401")
    void getCurrentUser_MissingAuthHeader_Returns401() throws Exception {
        // When & Then
        mockMvc.perform(get("/v1/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.message").value(containsString("Токен не предоставлен")));

        verify(restTemplate, never()).exchange(
                anyString(), any(), any(HttpEntity.class), eq(UserDtoResponse.class)
        );
    }

    @Test
    @DisplayName("GET /v1/auth/me - пустой Authorization header → 401")
    void getCurrentUser_EmptyAuthHeader_Returns401() throws Exception {
        // When & Then
        mockMvc.perform(get("/v1/auth/me")
                        .header("Authorization", ""))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Unauthorized"));

        verify(restTemplate, never()).exchange(
                anyString(), any(), any(HttpEntity.class), eq(UserDtoResponse.class)
        );
    }

    @Test
    @DisplayName("GET /v1/auth/me - невалидный формат токена (не Bearer) → 401")
    void getCurrentUser_InvalidTokenFormat_Returns401() throws Exception {
        // When & Then
        mockMvc.perform(get("/v1/auth/me")
                        .header("Authorization", "Basic dGVzdDp0ZXN0"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.message").value(containsString("Неверный формат токена")));

        verify(restTemplate, never()).exchange(
                anyString(), any(), any(HttpEntity.class), eq(UserDtoResponse.class)
        );
    }

    @Test
    @DisplayName("GET /v1/auth/me - user-service возвращает 401 → 401")
    void getCurrentUser_UserServiceReturns401_Returns401() throws Exception {
        // Given
        when(restTemplate.exchange(
                anyString(),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(UserDtoResponse.class)
        )).thenThrow(HttpClientErrorException.Unauthorized.create(
                HttpStatus.UNAUTHORIZED,
                "Unauthorized",
                HttpHeaders.EMPTY,
                null,
                null
        ));

        // When & Then
        mockMvc.perform(get("/v1/auth/me")
                        .header("Authorization", "Bearer invalid.token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.message").value(containsString("недействителен или истёк")));
    }

    @Test
    @DisplayName("GET /v1/auth/me - user-service возвращает 404 → 404")
    void getCurrentUser_UserNotFound_Returns404() throws Exception {
        // Given
        when(restTemplate.exchange(
                anyString(),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(UserDtoResponse.class)
        )).thenThrow(HttpClientErrorException.NotFound.create(
                HttpStatus.NOT_FOUND,
                "Not Found",
                HttpHeaders.EMPTY,
                null,
                null
        ));

        // When & Then
        mockMvc.perform(get("/v1/auth/me")
                        .header("Authorization", "Bearer valid.token"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value(containsString("не найден")));
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