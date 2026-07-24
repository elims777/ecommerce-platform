package ru.rfsnab.authservice.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import ru.rfsnab.authservice.exceptions.InvalidResetTokenException;
import ru.rfsnab.authservice.models.dto.AccountByEmailResponse;
import ru.rfsnab.authservice.models.dto.PasswordResetConsumeResponse;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("PasswordResetService Unit Tests")
class PasswordResetServiceTest {

    private static final String USER_SERVICE_URL = "http://localhost:8081";
    private static final String NOTIFICATION_SERVICE_URL = "http://localhost:8082";
    private static final String INTERNAL_SECRET = "test-internal-secret";
    private static final String EMAIL = "test@example.com";

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private PasswordResetService passwordResetService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(passwordResetService, "userServiceUrl", USER_SERVICE_URL);
        ReflectionTestUtils.setField(passwordResetService, "notificationServiceUrl", NOTIFICATION_SERVICE_URL);
        ReflectionTestUtils.setField(passwordResetService, "internalSecret", INTERNAL_SECRET);
    }

    // ==================== forgot() Tests ====================

    @Test
    @DisplayName("forgot() - несуществующий email → notification НЕ вызывается")
    void forgot_UnknownEmail_DoesNotCallNotification() {
        when(restTemplate.exchange(
                anyString(),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(AccountByEmailResponse.class)
        )).thenThrow(HttpClientErrorException.NotFound.create(
                HttpStatus.NOT_FOUND, "Not Found", HttpHeaders.EMPTY, null, null
        ));

        passwordResetService.forgot(EMAIL);

        verify(restTemplate, never()).postForEntity(anyString(), any(), eq(Void.class));
    }

    @Test
    @DisplayName("forgot() - существующий email → notification create вызван с корректным телом")
    void forgot_ExistingEmail_CallsNotificationCreate() {
        AccountByEmailResponse account = new AccountByEmailResponse(1L, "USER", EMAIL, "Test");

        when(restTemplate.exchange(
                anyString(),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(AccountByEmailResponse.class)
        )).thenReturn(ResponseEntity.ok(account));

        when(restTemplate.postForEntity(anyString(), any(), eq(Void.class)))
                .thenReturn(ResponseEntity.ok().build());

        passwordResetService.forgot(EMAIL);

        verify(restTemplate).postForEntity(
                eq(NOTIFICATION_SERVICE_URL + "/api/password-reset/create"),
                argThatCreateRequest(account),
                eq(Void.class)
        );
    }

    @Test
    @DisplayName("forgot() - неожиданная ошибка проглатывается, не пробрасывается наружу")
    void forgot_UnexpectedError_DoesNotThrow() {
        when(restTemplate.exchange(
                anyString(),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(AccountByEmailResponse.class)
        )).thenThrow(new RuntimeException("boom"));

        assertThatCode(() -> passwordResetService.forgot(EMAIL)).doesNotThrowAnyException();
    }

    // ==================== reset() Tests ====================

    @Test
    @DisplayName("reset() - valid токен USER → PUT user password вызван")
    void reset_ValidTokenUser_CallsUserPasswordEndpoint() {
        PasswordResetConsumeResponse consumeResponse = new PasswordResetConsumeResponse(true, 1L, "USER");

        when(restTemplate.postForObject(anyString(), isNull(), eq(PasswordResetConsumeResponse.class)))
                .thenReturn(consumeResponse);
        when(passwordEncoder.encode("newPassword123")).thenReturn("hashed");

        passwordResetService.reset("raw-token", "newPassword123");

        verify(restTemplate).exchange(
                eq(USER_SERVICE_URL + "/v1/users/1/password"),
                eq(HttpMethod.PUT),
                any(HttpEntity.class),
                eq(Void.class)
        );
    }

    @Test
    @DisplayName("reset() - valid токен LEGAL → PUT legal-entities password вызван")
    void reset_ValidTokenLegal_CallsLegalPasswordEndpoint() {
        PasswordResetConsumeResponse consumeResponse = new PasswordResetConsumeResponse(true, 2L, "LEGAL");

        when(restTemplate.postForObject(anyString(), isNull(), eq(PasswordResetConsumeResponse.class)))
                .thenReturn(consumeResponse);
        when(passwordEncoder.encode("newPassword123")).thenReturn("hashed");

        passwordResetService.reset("raw-token", "newPassword123");

        verify(restTemplate).exchange(
                eq(USER_SERVICE_URL + "/api/v1/legal-entities/2/password"),
                eq(HttpMethod.PUT),
                any(HttpEntity.class),
                eq(Void.class)
        );
    }

    @Test
    @DisplayName("reset() - invalid токен → InvalidResetTokenException")
    void reset_InvalidToken_ThrowsInvalidResetTokenException() {
        PasswordResetConsumeResponse consumeResponse = new PasswordResetConsumeResponse(false, null, null);

        when(restTemplate.postForObject(anyString(), isNull(), eq(PasswordResetConsumeResponse.class)))
                .thenReturn(consumeResponse);

        assertThatThrownBy(() -> passwordResetService.reset("bad-token", "newPassword123"))
                .isInstanceOf(InvalidResetTokenException.class);

        verify(restTemplate, never()).exchange(anyString(), eq(HttpMethod.PUT), any(HttpEntity.class), eq(Void.class));
    }

    // ==================== validate() Tests ====================

    @Test
    @DisplayName("validate() - проксирует ответ notification validate")
    void validate_ReturnsFlagFromNotification() {
        when(restTemplate.getForObject(anyString(), eq(Map.class)))
                .thenReturn(Map.of("valid", true));

        boolean result = passwordResetService.validate("some-token");

        assertThat(result).isTrue();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> argThatCreateRequest(AccountByEmailResponse account) {
        return org.mockito.ArgumentMatchers.argThat(body ->
                account.accountId().equals(body.get("accountId"))
                        && account.accountType().equals(body.get("accountType"))
                        && account.email().equals(body.get("email"))
                        && account.firstName().equals(body.get("firstName"))
                        && body.get("rawToken") != null);
    }
}
