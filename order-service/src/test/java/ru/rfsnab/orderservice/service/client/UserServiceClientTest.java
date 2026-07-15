package ru.rfsnab.orderservice.service.client;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import ru.rfsnab.orderservice.exception.ProfileIncompleteException;
import ru.rfsnab.orderservice.exception.ServiceUnavailableException;
import ru.rfsnab.orderservice.models.dto.user.ProfileCompletenessDto;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserServiceClient")
class UserServiceClientTest {

    private static final String USER_SERVICE_URL = "http://localhost:8081";
    private static final String INTERNAL_SECRET = "test-secret";
    private static final Long USER_ID = 1L;

    @Mock
    private RestTemplate restTemplate;

    private UserServiceClient client;

    @BeforeEach
    void setUp() {
        client = new UserServiceClient(restTemplate, USER_SERVICE_URL, INTERNAL_SECRET);
    }

    @Test
    @DisplayName("getProfileCompleteness — user-service недоступен → ServiceUnavailableException (fail-closed)")
    void getProfileCompleteness_serviceUnavailable_throwsServiceUnavailable() {
        when(restTemplate.exchange(
                eq(USER_SERVICE_URL + "/v1/users/{id}/profile-completeness"),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(ProfileCompletenessDto.class),
                eq(USER_ID)
        )).thenThrow(new ResourceAccessException("Connection refused"));

        assertThatThrownBy(() -> client.getProfileCompleteness(USER_ID))
                .isInstanceOf(ServiceUnavailableException.class);
    }

    @Test
    @DisplayName("requireCompleteProfile — user-service недоступен → ServiceUnavailableException, гейт не пропускает")
    void requireCompleteProfile_serviceUnavailable_throwsServiceUnavailable() {
        when(restTemplate.exchange(
                eq(USER_SERVICE_URL + "/v1/users/{id}/profile-completeness"),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(ProfileCompletenessDto.class),
                eq(USER_ID)
        )).thenThrow(new ResourceAccessException("Connection refused"));

        assertThatThrownBy(() -> client.requireCompleteProfile(USER_ID))
                .isInstanceOf(ServiceUnavailableException.class);
    }

    @Test
    @DisplayName("requireCompleteProfile — complete=false → ProfileIncompleteException")
    void requireCompleteProfile_incomplete_throwsProfileIncomplete() {
        ProfileCompletenessDto dto = new ProfileCompletenessDto(false, List.of("phone", "legalEntity"));
        when(restTemplate.exchange(
                eq(USER_SERVICE_URL + "/v1/users/{id}/profile-completeness"),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(ProfileCompletenessDto.class),
                eq(USER_ID)
        )).thenReturn(ResponseEntity.ok(dto));

        assertThatThrownBy(() -> client.requireCompleteProfile(USER_ID))
                .isInstanceOf(ProfileIncompleteException.class);
    }

    @Test
    @DisplayName("requireCompleteProfile — complete=true → не бросает исключение")
    void requireCompleteProfile_complete_doesNotThrow() {
        ProfileCompletenessDto dto = new ProfileCompletenessDto(true, List.of());
        when(restTemplate.exchange(
                eq(USER_SERVICE_URL + "/v1/users/{id}/profile-completeness"),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(ProfileCompletenessDto.class),
                eq(USER_ID)
        )).thenReturn(ResponseEntity.ok(dto));

        client.requireCompleteProfile(USER_ID);
    }

    @Test
    @DisplayName("getProfileCompleteness — отправляет X-Internal-Token в заголовке запроса")
    void getProfileCompleteness_sendsInternalTokenHeader() {
        ProfileCompletenessDto dto = new ProfileCompletenessDto(true, List.of());
        when(restTemplate.exchange(
                eq(USER_SERVICE_URL + "/v1/users/{id}/profile-completeness"),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(ProfileCompletenessDto.class),
                eq(USER_ID)
        )).thenAnswer(invocation -> {
            HttpEntity<?> entity = invocation.getArgument(2);
            assertThat(entity.getHeaders().getFirst("X-Internal-Token")).isEqualTo(INTERNAL_SECRET);
            return ResponseEntity.ok(dto);
        });

        client.getProfileCompleteness(USER_ID);
    }
}
