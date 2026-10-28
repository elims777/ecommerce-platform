package ru.rfsnab.authservice.service;

import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import ru.rfsnab.authservice.exceptions.AuthServiceException;
import ru.rfsnab.authservice.exceptions.UserNotFoundException;
import ru.rfsnab.authservice.models.dto.AuthResponse;
import ru.rfsnab.authservice.models.dto.LegalAuthRequest;
import ru.rfsnab.authservice.models.dto.LegalEntityAuthResponse;
import ru.rfsnab.authservice.models.dto.LegalEntityDto;
import ru.rfsnab.authservice.models.dto.RoleEntity;
import ru.rfsnab.authservice.models.dto.SimpleAuthRequest;
import ru.rfsnab.authservice.models.dto.SwitchContextRequest;
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
                any(HttpEntity.class),
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
    @DisplayName("authenticate() - другая HttpClientErrorException → AuthServiceException")
    void authenticate_OtherHttpError_ThrowsAuthServiceException() {
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
                .isInstanceOf(AuthServiceException.class)
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

    @Test
    @DisplayName("refresh() - B2B refresh token даёт B2B токены с companyName и inn")
    void refresh_B2bToken_KeepsB2bContext() {
        String oldRefreshToken = "old.b2b.refresh";
        when(jwtService.validateToken(oldRefreshToken)).thenReturn(true);
        when(jwtService.extractUserId(oldRefreshToken)).thenReturn(5L);
        when(jwtService.extractEmail(oldRefreshToken)).thenReturn("company@example.com");
        when(jwtService.extractClientType(oldRefreshToken)).thenReturn("B2B");
        when(jwtService.extractCompanyName(oldRefreshToken)).thenReturn("ООО Ромашка");
        when(jwtService.extractInn(oldRefreshToken)).thenReturn("1234567890");
        stubLegalEntity(5L, "VERIFIED");
        when(jwtService.generateToken(5L, "company@example.com", "B2B", "ООО Ромашка", "1234567890")).thenReturn("new.access");
        when(jwtService.generateRefreshToken(5L, "company@example.com", "B2B", "ООО Ромашка", "1234567890")).thenReturn("new.refresh");

        AuthResponse result = authService.refresh(oldRefreshToken);

        assertThat(result.getAccessToken()).isEqualTo("new.access");
        assertThat(result.getRefreshToken()).isEqualTo("new.refresh");
        assertThat(result.getClientType()).isEqualTo("B2B");
        assertThat(result.getCompanyName()).isEqualTo("ООО Ромашка");
        assertThat(result.getInn()).isEqualTo("1234567890");
        verify(jwtService, never()).extractRolesFromToken(anyString());
        verify(restTemplate).exchange(eq(USER_SERVICE_URL + "/api/v1/legal-entities/5"), eq(HttpMethod.GET), any(HttpEntity.class), eq(LegalEntityDto.class));
    }

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
                any(HttpEntity.class),
                eq(UserDtoResponse.class)
        )).thenReturn(responseEntity);

        when(roleExtractor.extractRoles(validUserResponse)).thenReturn(userRoles);
        // ИЗМЕНЕНО: добавлен userId как первый параметр
        when(jwtService.generateToken(USER_ID, USER_EMAIL, userRoles)).thenReturn(accessToken);
        when(jwtService.generateRefreshToken(USER_ID, USER_EMAIL, userRoles)).thenReturn(refreshToken);

        // When
        Map<String, Object> result = authService.authenticateWithUserData(validAuthRequest);

        // Then
        assertThat(result).isNotNull()
                .containsEntry("access_token", accessToken)
                .containsEntry("refresh_token", refreshToken)
                .containsEntry("token_type", "Bearer")
                .containsEntry("expires_in", 3600)
                .containsEntry("user", validUserResponse);

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
    @DisplayName("getCurrentUser() - 404 Not Found → UserNotFoundException")
    void getCurrentUser_NotFound_ThrowsUserNotFoundException() {
        when(restTemplate.exchange(anyString(), any(), any(HttpEntity.class), eq(UserDtoResponse.class)))
                .thenThrow(HttpClientErrorException.NotFound.create(
                        HttpStatus.NOT_FOUND,
                        "Not Found",
                        HttpHeaders.EMPTY,
                        null,
                        null
                ));

        assertThatThrownBy(() -> authService.getCurrentUser("some.token"))
                .isInstanceOf(UserNotFoundException.class)
                .hasMessageContaining("Пользователь не найден");
    }

    @Test
    @DisplayName("getCurrentUser - user-service вернул 403 → AccessDeniedException")
    void getCurrentUser_Forbidden_ThrowsAccessDenied() {
        when(restTemplate.exchange(anyString(), any(), any(HttpEntity.class), eq(UserDtoResponse.class)))
                .thenThrow(HttpClientErrorException.Forbidden.create(
                        HttpStatus.FORBIDDEN,
                        "Forbidden",
                        HttpHeaders.EMPTY,
                        null,
                        null
                ));

        assertThatThrownBy(() -> authService.getCurrentUser("some.token"))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("getCurrentUser() - другая HttpClientErrorException → AuthServiceException")
    void getCurrentUser_OtherHttpError_ThrowsAuthServiceException() {
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
                .isInstanceOf(AuthServiceException.class)
                .hasMessageContaining("Ошибка получения данных пользователя");
    }

    @Test
    @DisplayName("switchContext() B2B→B2B - текущий токен уже B2B, переключение отклоняется")
    void switchContext_ToB2bFromB2b_IsRejected() {
        when(jwtService.extractClientType("b2b.token")).thenReturn("B2B");

        assertThatThrownBy(() -> authService.switchContext("b2b.token", new SwitchContextRequest("B2B")))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(restTemplate);
    }

    @Test
    @DisplayName("switchContext() → B2C - перехода в личный аккаунт нет, 403")
    void switchContext_ToB2c_IsRejected() {
        assertThatThrownBy(() -> authService.switchContext("b2b.token", new SwitchContextRequest("B2C")))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Переход из организации в личный аккаунт невозможен");

        verifyNoInteractions(restTemplate);
    }

    @Test
    @DisplayName("authenticate() - запрос в user-service уходит с X-Internal-Token")
    void authenticate_SendsInternalToken() {
        String secret = "test-internal-secret";
        ReflectionTestUtils.setField(authService, "internalSecret", secret);
        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(UserDtoResponse.class)))
                .thenReturn(ResponseEntity.ok(validUserResponse));
        when(roleExtractor.extractRoles(validUserResponse)).thenReturn(userRoles);

        authService.authenticate(validAuthRequest);

        @SuppressWarnings({"unchecked", "rawtypes"})
        ArgumentCaptor<HttpEntity<?>> captor = ArgumentCaptor.forClass((Class) HttpEntity.class);
        verify(restTemplate).postForEntity(anyString(), captor.capture(), eq(UserDtoResponse.class));
        assertThat(captor.getValue().getHeaders().getFirst("X-Internal-Token")).isEqualTo(secret);
        assertThat(captor.getValue().getBody()).isEqualTo(validAuthRequest);
    }

    @Test
    @DisplayName("switchContext() B2C→B2B - вызовы user-service идут с X-Internal-Token")
    void switchContext_ToB2b_SendsInternalToken() {
        // Given
        String secret = "test-internal-secret";
        ReflectionTestUtils.setField(authService, "internalSecret", secret);
        when(jwtService.extractUserId("token")).thenReturn(USER_ID);
        when(restTemplate.exchange(eq(USER_SERVICE_URL + "/api/v1/legal-entities/link-status/" + USER_ID),
                eq(HttpMethod.GET), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(Map.of("confirmed", true, "legalEntityId", 5)));
        LegalEntityDto legal = new LegalEntityDto();
        legal.setEmail("company@example.com");
        legal.setFullName("ООО Ромашка");
        legal.setInn("1234567890");
        legal.setVerificationStatus("VERIFIED");
        when(restTemplate.exchange(eq(USER_SERVICE_URL + "/api/v1/legal-entities/5"),
                eq(HttpMethod.GET), any(HttpEntity.class), eq(LegalEntityDto.class)))
                .thenReturn(ResponseEntity.ok(legal));
        when(jwtService.generateToken(anyLong(), anyString(), eq("B2B"), anyString(), anyString())).thenReturn("access");
        when(jwtService.generateRefreshToken(anyLong(), anyString(), eq("B2B"), anyString(), anyString())).thenReturn("refresh");

        // When
        authService.switchContext("token", new SwitchContextRequest("B2B"));

        // Then
        @SuppressWarnings({"unchecked", "rawtypes"})
        ArgumentCaptor<HttpEntity<?>> captor = ArgumentCaptor.forClass((Class) HttpEntity.class);
        verify(restTemplate, times(2)).exchange(anyString(), eq(HttpMethod.GET), captor.capture(), any(Class.class));
        assertThat(captor.getAllValues())
                .allSatisfy(entity -> assertThat(entity.getHeaders().getFirst("X-Internal-Token")).isEqualTo(secret));
    }

    // ==================== B2B refresh: проверка юрлица ====================

    private void stubLegalEntity(Long id, String verificationStatus) {
        LegalEntityDto legal = new LegalEntityDto();
        legal.setId(id);
        legal.setVerificationStatus(verificationStatus);
        when(restTemplate.exchange(eq(USER_SERVICE_URL + "/api/v1/legal-entities/" + id),
                eq(HttpMethod.GET), any(HttpEntity.class), eq(LegalEntityDto.class)))
                .thenReturn(ResponseEntity.ok(legal));
    }

    private void stubB2bRefreshToken(String token) {
        when(jwtService.validateToken(token)).thenReturn(true);
        when(jwtService.extractUserId(token)).thenReturn(5L);
        when(jwtService.extractEmail(token)).thenReturn("company@example.com");
        when(jwtService.extractClientType(token)).thenReturn("B2B");
    }

    @Test
    @DisplayName("refresh() B2B - юрлицо не верифицировано → JwtException, новые токены не выдаются")
    void refresh_B2bLegalEntityNotVerified_ThrowsJwtException() {
        stubB2bRefreshToken("b2b.refresh");
        stubLegalEntity(5L, "REJECTED");

        assertThatThrownBy(() -> authService.refresh("b2b.refresh"))
                .isInstanceOf(JwtException.class);

        verify(jwtService, never()).generateToken(anyLong(), anyString(), anyString(), any(), any());
    }

    @Test
    @DisplayName("refresh() B2B - юрлицо удалено (404) → JwtException")
    void refresh_B2bLegalEntityNotFound_ThrowsJwtException() {
        stubB2bRefreshToken("b2b.refresh");
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(LegalEntityDto.class)))
                .thenThrow(HttpClientErrorException.NotFound.create(
                        HttpStatus.NOT_FOUND, "Not Found", HttpHeaders.EMPTY, null, null));

        assertThatThrownBy(() -> authService.refresh("b2b.refresh"))
                .isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("refresh() B2B - user-service ответил 500 → AuthServiceException")
    void refresh_B2bUserServiceError_ThrowsAuthServiceException() {
        stubB2bRefreshToken("b2b.refresh");
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(LegalEntityDto.class)))
                .thenThrow(HttpClientErrorException.create(
                        HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error", HttpHeaders.EMPTY, null, null));

        assertThatThrownBy(() -> authService.refresh("b2b.refresh"))
                .isInstanceOf(AuthServiceException.class);
    }

    // ==================== switchContext(): ошибки user-service ====================

    private void stubLinkStatus(Object body) {
        when(jwtService.extractUserId("token")).thenReturn(USER_ID);
        when(restTemplate.exchange(eq(USER_SERVICE_URL + "/api/v1/legal-entities/link-status/" + USER_ID),
                eq(HttpMethod.GET), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(ResponseEntity.ok((Map) body));
    }

    @Test
    @DisplayName("switchContext() B2C→B2B - связь не подтверждена (confirmed=false) → AccessDeniedException")
    void switchContext_LinkNotConfirmed_ThrowsAccessDenied() {
        stubLinkStatus(Map.of("confirmed", false));

        assertThatThrownBy(() -> authService.switchContext("token", new SwitchContextRequest("B2B")))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Нет подтверждённой связи");

        verify(jwtService, never()).generateToken(anyLong(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("switchContext() B2C→B2B - юрлицо не верифицировано (REJECTED) → AccessDeniedException, токены не выдаются")
    void switchContext_LegalEntityNotVerified_ThrowsAccessDenied() {
        stubLinkStatus(Map.of("confirmed", true, "legalEntityId", 5));
        stubLegalEntity(5L, "REJECTED");

        assertThatThrownBy(() -> authService.switchContext("token", new SwitchContextRequest("B2B")))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Юрлицо не верифицировано");

        verify(jwtService, never()).generateToken(anyLong(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("switchContext() B2C→B2B - user-service вернул 404 → AccessDeniedException (403), не 500")
    void switchContext_UserServiceNotFound_ThrowsAccessDenied() {
        when(jwtService.extractUserId("token")).thenReturn(USER_ID);
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(Map.class)))
                .thenThrow(HttpClientErrorException.NotFound.create(
                        HttpStatus.NOT_FOUND, "Not Found", HttpHeaders.EMPTY, null, null));

        assertThatThrownBy(() -> authService.switchContext("token", new SwitchContextRequest("B2B")))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("switchContext() B2C→B2B - другая 4xx от user-service → AuthServiceException (502)")
    void switchContext_UserServiceOtherError_ThrowsAuthServiceException() {
        when(jwtService.extractUserId("token")).thenReturn(USER_ID);
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(Map.class)))
                .thenThrow(HttpClientErrorException.create(
                        HttpStatus.BAD_REQUEST, "Bad Request", HttpHeaders.EMPTY, null, null));

        assertThatThrownBy(() -> authService.switchContext("token", new SwitchContextRequest("B2B")))
                .isInstanceOf(AuthServiceException.class);
    }

    // ==================== authenticateLegalEntity() ====================

    @Test
    @DisplayName("authenticateLegalEntity() - запрос в user-service уходит с X-Internal-Token")
    void authenticateLegalEntity_SendsInternalToken() {
        String secret = "test-internal-secret";
        ReflectionTestUtils.setField(authService, "internalSecret", secret);
        LegalAuthRequest request = new LegalAuthRequest("company@example.com", "pass");
        when(restTemplate.postForEntity(eq(USER_SERVICE_URL + "/api/v1/legal-entities/authenticate"),
                any(HttpEntity.class), eq(LegalEntityAuthResponse.class)))
                .thenReturn(ResponseEntity.ok(new LegalEntityAuthResponse(5L, "company@example.com", "1234567890", "ООО Ромашка")));
        when(jwtService.generateToken(anyLong(), anyString(), eq("B2B"), anyString(), anyString())).thenReturn("access");
        when(jwtService.generateRefreshToken(anyLong(), anyString(), eq("B2B"), anyString(), anyString())).thenReturn("refresh");

        authService.authenticateLegalEntity(request);

        @SuppressWarnings({"unchecked", "rawtypes"})
        ArgumentCaptor<HttpEntity<?>> captor = ArgumentCaptor.forClass((Class) HttpEntity.class);
        verify(restTemplate).postForEntity(anyString(), captor.capture(), eq(LegalEntityAuthResponse.class));
        assertThat(captor.getValue().getHeaders().getFirst("X-Internal-Token")).isEqualTo(secret);
        assertThat(captor.getValue().getBody()).isEqualTo(request);
    }
}
