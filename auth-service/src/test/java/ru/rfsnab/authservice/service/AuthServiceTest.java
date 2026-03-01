package ru.rfsnab.authservice.service;

import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.*;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import ru.rfsnab.authservice.models.dto.AuthResponse;
import ru.rfsnab.authservice.models.dto.RoleEntity;
import ru.rfsnab.authservice.models.dto.SimpleAuthRequest;
import ru.rfsnab.authservice.models.dto.UserDtoResponse;
import ru.rfsnab.authservice.utils.JWTService;
import ru.rfsnab.authservice.utils.RoleExtractor;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit тесты для AuthService
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AuthService Unit Tests")
class AuthServiceTest {

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private JWTService jwtService;

    @Mock
    private RoleExtractor roleExtractor;

    @InjectMocks
    private AuthService authService;

    private static final String USER_SERVICE_URL = "http://localhost:8081";
    private static final String USER_SERVICE_AUTH_URL = "/v1/users/authenticate";
    private static final Long USER_ID = 1L;
    private static final String USER_EMAIL = "test@example.com";

    private SimpleAuthRequest validAuthRequest;
    private UserDtoResponse validUserResponse;
    private List<String> userRoles;

    @BeforeEach
    void setUp() {
        // Устанавливаем значения @Value полей через ReflectionTestUtils
        ReflectionTestUtils.setField(authService, "userServiceUrl", USER_SERVICE_URL);
        ReflectionTestUtils.setField(authService, "userServiceAuthUrl", USER_SERVICE_AUTH_URL);

        // Подготовка тестовых данных
        validAuthRequest = new SimpleAuthRequest();
        validAuthRequest.setEmail(USER_EMAIL);
        validAuthRequest.setPassword("password123");

        RoleEntity userRole = new RoleEntity();
        userRole.setId(1L);
        userRole.setName("USER");

        validUserResponse = new UserDtoResponse();
        validUserResponse.setId(USER_ID);
        validUserResponse.setEmail(USER_EMAIL);
        validUserResponse.setFirstname("Test");
        validUserResponse.setLastname("User");
        validUserResponse.setRoles(Set.of(userRole));

        userRoles = List.of("ROLE_USER");
    }

    // ==================== authenticate() Tests ====================

    @Test
    @DisplayName("authenticate() - успешная аутентификация возвращает токены")
    void authenticate_Success_ReturnsAuthResponse() {
        // Given
        String expectedAccessToken = "access.token.here";
        String expectedRefreshToken = "refresh.token.here";

        ResponseEntity<UserDtoResponse> responseEntity = ResponseEntity.ok(validUserResponse);

        when(restTemplate.postForEntity(
                eq(USER_SERVICE_URL + USER_SERVICE_AUTH_URL),
                eq(validAuthRequest),
                eq(UserDtoResponse.class)
        )).thenReturn(responseEntity);

        when(roleExtractor.extractRoles(validUserResponse)).thenReturn(userRoles);
        // ИЗМЕНЕНО: добавлен userId как первый параметр
        when(jwtService.generateToken(USER_ID, USER_EMAIL, userRoles)).thenReturn(expectedAccessToken);
        when(jwtService.generateRefreshToken(USER_ID, USER_EMAIL, userRoles)).thenReturn(expectedRefreshToken);

        // When
        AuthResponse result = authService.authenticate(validAuthRequest);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getAccessToken()).isEqualTo(expectedAccessToken);
        assertThat(result.getRefreshToken()).isEqualTo(expectedRefreshToken);
        assertThat(result.getTokenType()).isEqualTo("Bearer");

        verify(restTemplate).postForEntity(anyString(), any(), eq(UserDtoResponse.class));
        verify(roleExtractor).extractRoles(validUserResponse);
        // ИЗМЕНЕНО: проверяем новую сигнатуру
        verify(jwtService).generateToken(USER_ID, USER_EMAIL, userRoles);
        verify(jwtService).generateRefreshToken(USER_ID, USER_EMAIL, userRoles);
    }

    @Test
    @DisplayName("authenticate() - user-service возвращает null → BadCredentialsException")
    void authenticate_NullUserResponse_ThrowsBadCredentialsException() {
        // Given
        ResponseEntity<UserDtoResponse> responseEntity = ResponseEntity.ok(null);

        when(restTemplate.postForEntity(anyString(), any(), eq(UserDtoResponse.class)))
                .thenReturn(responseEntity);

        // When & Then
        assertThatThrownBy(() -> authService.authenticate(validAuthRequest))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("Не удалось получить данные пользователя");

        // ИЗМЕНЕНО: новая сигнатура с anyLong()
        verify(jwtService, never()).generateToken(anyLong(), anyString(), anyList());
    }

    @Test
    @DisplayName("authenticate() - 401 Unauthorized → BadCredentialsException")
    void authenticate_Unauthorized_ThrowsBadCredentialsException() {
        // Given
        when(restTemplate.postForEntity(anyString(), any(), eq(UserDtoResponse.class)))
                .thenThrow(HttpClientErrorException.Unauthorized.create(
                        HttpStatus.UNAUTHORIZED,
                        "Unauthorized",
                        HttpHeaders.EMPTY,
                        null,
                        null
                ));

        // When & Then
        assertThatThrownBy(() -> authService.authenticate(validAuthRequest))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("Неверный email или пароль");

        verify(jwtService, never()).generateToken(anyLong(), anyString(), anyList());
    }

    @Test
    @DisplayName("authenticate() - 403 Forbidden → BadCredentialsException")
    void authenticate_Forbidden_ThrowsBadCredentialsException() {
        // Given
        when(restTemplate.postForEntity(anyString(), any(), eq(UserDtoResponse.class)))
                .thenThrow(HttpClientErrorException.Forbidden.create(
                        HttpStatus.FORBIDDEN,
                        "Forbidden",
                        HttpHeaders.EMPTY,
                        null,
                        null
                ));

        // When & Then
        assertThatThrownBy(() -> authService.authenticate(validAuthRequest))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("Неверный email или пароль");
    }

    @Test
    @DisplayName("authenticate() - другая HttpClientErrorException → RuntimeException")
    void authenticate_OtherHttpError_ThrowsRuntimeException() {
        // Given
        when(restTemplate.postForEntity(anyString(), any(), eq(UserDtoResponse.class)))
                .thenThrow(HttpClientErrorException.create(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "Internal server error",
                        HttpHeaders.EMPTY,
                        null,
                        null
                ));

        // When & Then
        assertThatThrownBy(() -> authService.authenticate(validAuthRequest))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Ошибка аутентификации");
    }

    // ==================== refresh() Tests ====================

    @Test
    @DisplayName("refresh() - валидный refresh token возвращает новые токены")
    void refresh_ValidToken_ReturnsNewTokens() {
        // Given
        String oldRefreshToken = "old.refresh.token";
        String newAccessToken = "new.access.token";
        String newRefreshToken = "new.refresh.token";

        when(jwtService.validateToken(oldRefreshToken)).thenReturn(true);
        // ИЗМЕНЕНО: теперь данные берутся из токена, без запроса в user-service
        when(jwtService.extractUserId(oldRefreshToken)).thenReturn(USER_ID);
        when(jwtService.extractEmail(oldRefreshToken)).thenReturn(USER_EMAIL);
        when(jwtService.extractRolesFromToken(oldRefreshToken)).thenReturn(userRoles);

        when(jwtService.generateToken(USER_ID, USER_EMAIL, userRoles)).thenReturn(newAccessToken);
        when(jwtService.generateRefreshToken(USER_ID, USER_EMAIL, userRoles)).thenReturn(newRefreshToken);

        // When
        AuthResponse result = authService.refresh(oldRefreshToken);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getAccessToken()).isEqualTo(newAccessToken);
        assertThat(result.getRefreshToken()).isEqualTo(newRefreshToken);
        assertThat(result.getTokenType()).isEqualTo("Bearer");

        verify(jwtService).validateToken(oldRefreshToken);
        verify(jwtService).extractUserId(oldRefreshToken);
        verify(jwtService).extractEmail(oldRefreshToken);
        verify(jwtService).extractRolesFromToken(oldRefreshToken);
        verify(jwtService).generateToken(USER_ID, USER_EMAIL, userRoles);
        verify(jwtService).generateRefreshToken(USER_ID, USER_EMAIL, userRoles);
        // ИЗМЕНЕНО: проверяем что НЕТ запроса к user-service
        verifyNoInteractions(restTemplate);
    }

    @Test
    @DisplayName("refresh() - невалидный токен → JwtException")
    void refresh_InvalidToken_ThrowsJwtException() {
        // Given
        String invalidToken = "invalid.token";
        when(jwtService.validateToken(invalidToken)).thenReturn(false);

        // When & Then
        assertThatThrownBy(() -> authService.refresh(invalidToken))
                .isInstanceOf(JwtException.class)
                .hasMessageContaining("Недопустимый токен");

        verify(jwtService).validateToken(invalidToken);
        verify(jwtService, never()).extractUserId(anyString());
        verify(jwtService, never()).extractEmail(anyString());
    }

    // УДАЛЁН тест refresh_UserNotFound - больше нет запроса в user-service

    // ==================== authenticateWithUserData() Tests ====================

    @Test
    @DisplayName("authenticateWithUserData() - успешная аутентификация с данными пользователя")
    void authenticateWithUserData_Success_ReturnsMapWithTokensAndUser() {
        // Given
        String accessToken = "access.token";
        String refreshToken = "refresh.token";

        ResponseEntity<UserDtoResponse> responseEntity = ResponseEntity.ok(validUserResponse);
        when(restTemplate.postForEntity(
                eq(USER_SERVICE_URL + "/v1/users/authenticate"),
                eq(validAuthRequest),
                eq(UserDtoResponse.class)
        )).thenReturn(responseEntity);

        when(roleExtractor.extractRoles(validUserResponse)).thenReturn(userRoles);
        // ИЗМЕНЕНО: добавлен userId как первый параметр
        when(jwtService.generateToken(USER_ID, USER_EMAIL, userRoles)).thenReturn(accessToken);
        when(jwtService.generateRefreshToken(USER_ID, USER_EMAIL, userRoles)).thenReturn(refreshToken);

        // When
        Map<String, Object> result = authService.authenticateWithUserData(validAuthRequest);

        // Then
        assertThat(result).isNotNull();
        assertThat(result).containsEntry("access_token", accessToken);
        assertThat(result).containsEntry("refresh_token", refreshToken);
        assertThat(result).containsEntry("token_type", "Bearer");
        assertThat(result).containsEntry("expires_in", 3600);
        assertThat(result).containsEntry("user", validUserResponse);

        verify(restTemplate).postForEntity(anyString(), any(), eq(UserDtoResponse.class));
        verify(jwtService).generateToken(USER_ID, USER_EMAIL, userRoles);
        verify(jwtService).generateRefreshToken(USER_ID, USER_EMAIL, userRoles);
    }

    // ==================== getCurrentUser() Tests ====================

    @Test
    @DisplayName("getCurrentUser() - валидный токен возвращает данные пользователя")
    void getCurrentUser_ValidToken_ReturnsUserData() {
        // Given
        String accessToken = "valid.access.token";
        String meUrl = USER_SERVICE_URL + "/v1/users/me";

        HttpHeaders expectedHeaders = new HttpHeaders();
        expectedHeaders.setBearerAuth(accessToken);
        HttpEntity<Void> expectedEntity = new HttpEntity<>(expectedHeaders);

        ResponseEntity<UserDtoResponse> responseEntity = ResponseEntity.ok(validUserResponse);

        when(restTemplate.exchange(
                eq(meUrl),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(UserDtoResponse.class)
        )).thenReturn(responseEntity);

        // When
        UserDtoResponse result = authService.getCurrentUser(accessToken);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getEmail()).isEqualTo(USER_EMAIL);
        assertThat(result.getId()).isEqualTo(USER_ID);

        verify(restTemplate).exchange(
                eq(meUrl),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(UserDtoResponse.class)
        );
    }

    @Test
    @DisplayName("getCurrentUser() - user-service возвращает null → RuntimeException")
    void getCurrentUser_NullResponse_ThrowsRuntimeException() {
        // Given
        String accessToken = "valid.token";
        ResponseEntity<UserDtoResponse> responseEntity = ResponseEntity.ok(null);

        when(restTemplate.exchange(anyString(), any(), any(HttpEntity.class), eq(UserDtoResponse.class)))
                .thenReturn(responseEntity);

        // When & Then
        assertThatThrownBy(() -> authService.getCurrentUser(accessToken))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Не удалось получить данные пользователя");
    }

    @Test
    @DisplayName("getCurrentUser() - 401 Unauthorized → JwtException")
    void getCurrentUser_Unauthorized_ThrowsJwtException() {
        // Given
        String invalidToken = "invalid.token";

        when(restTemplate.exchange(anyString(), any(), any(HttpEntity.class), eq(UserDtoResponse.class)))
                .thenThrow(HttpClientErrorException.Unauthorized.create(
                        HttpStatus.UNAUTHORIZED,
                        "Unauthorized",
                        HttpHeaders.EMPTY,
                        null,
                        null
                ));

        // When & Then
        assertThatThrownBy(() -> authService.getCurrentUser(invalidToken))
                .isInstanceOf(JwtException.class)
                .hasMessageContaining("Токен недействителен или истек");
    }

    @Test
    @DisplayName("getCurrentUser() - другая HttpClientErrorException → RuntimeException")
    void getCurrentUser_OtherHttpError_ThrowsRuntimeException() {
        // Given
        String token = "some.token";

        when(restTemplate.exchange(anyString(), any(), any(HttpEntity.class), eq(UserDtoResponse.class)))
                .thenThrow(HttpClientErrorException.create(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "Internal server error",
                        HttpHeaders.EMPTY,
                        null,
                        null
                ));

        // When & Then
        assertThatThrownBy(() -> authService.getCurrentUser(token))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Ошибка получения данных пользователя");
    }
}