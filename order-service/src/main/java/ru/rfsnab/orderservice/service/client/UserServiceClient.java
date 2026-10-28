package ru.rfsnab.orderservice.service.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import ru.rfsnab.orderservice.exception.InvalidOrderStateException;
import ru.rfsnab.orderservice.exception.ProfileIncompleteException;
import ru.rfsnab.orderservice.exception.ServiceUnavailableException;
import ru.rfsnab.orderservice.models.dto.user.ProfileCompletenessDto;

/**
 * Клиент для взаимодействия с user-service.
 * Проверяет заполненность профиля перед добавлением в корзину и созданием заказа.
 */
@Service
@Slf4j
public class UserServiceClient {

    private final RestTemplate restTemplate;
    private final String userServiceUrl;
    private final String internalSecret;

    public UserServiceClient(RestTemplate restTemplate,
                              @Value("${services.user.url}") String userServiceUrl,
                              @Value("${internal.secret}") String internalSecret) {
        this.restTemplate = restTemplate;
        this.userServiceUrl = userServiceUrl;
        this.internalSecret = internalSecret;
    }

    public ProfileCompletenessDto getProfileCompleteness(Long userId) {
        try {
            return restTemplate.exchange(
                    userServiceUrl + "/v1/users/{id}/profile-completeness",
                    HttpMethod.GET,
                    new HttpEntity<>(internalHeaders()),
                    ProfileCompletenessDto.class,
                    userId
            ).getBody();
        } catch (HttpClientErrorException.NotFound e) {
            log.warn("User service: profile not found for userId={}", userId);
            throw new InvalidOrderStateException("Профиль пользователя не найден или недоступен");
        } catch (HttpClientErrorException e) {
            // 401/403 — как правило неверный INTERNAL_SECRET, должно быть заметно в логах
            log.error("User service rejected profile-completeness for userId={}: {}", userId, e.getStatusCode());
            throw new ServiceUnavailableException("User service unavailable");
        } catch (Exception e) {
            log.error("User service unavailable: {}", e.getMessage());
            throw new ServiceUnavailableException("User service unavailable");
        }
    }

    public void requireCompleteProfile(Long userId) {
        ProfileCompletenessDto completeness = getProfileCompleteness(userId);
        if (!completeness.complete()) {
            throw new ProfileIncompleteException(completeness.missing());
        }
    }

    private HttpHeaders internalHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Internal-Token", internalSecret);
        headers.set("Content-Type", "application/json");
        return headers;
    }
}
