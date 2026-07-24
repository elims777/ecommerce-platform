package ru.rfsnab.authservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import ru.rfsnab.authservice.exceptions.InvalidResetTokenException;
import ru.rfsnab.authservice.models.dto.AccountByEmailResponse;
import ru.rfsnab.authservice.models.dto.PasswordResetConsumeResponse;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class PasswordResetService {

    private static final int TOKEN_BYTES_LENGTH = 32;
    private static final String ACCOUNT_TYPE_LEGAL = "LEGAL";

    private final RestTemplate restTemplate;
    private final PasswordEncoder passwordEncoder;
    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${user.service.url}")
    private String userServiceUrl;

    @Value("${notification.service.url}")
    private String notificationServiceUrl;

    @Value("${internal.secret}")
    private String internalSecret;

    public void forgot(String email) {
        try {
            HttpEntity<Void> internalRequest = new HttpEntity<>(internalHeaders());
            AccountByEmailResponse account = restTemplate.exchange(
                    userServiceUrl + "/v1/users/account-by-email?email=" + email,
                    HttpMethod.GET,
                    internalRequest,
                    AccountByEmailResponse.class
            ).getBody();

            if (account == null) {
                return;
            }

            String rawToken = generateRawToken();

            Map<String, Object> createRequest = Map.of(
                    "accountId", account.accountId(),
                    "accountType", account.accountType(),
                    "email", account.email(),
                    "firstName", account.firstName(),
                    "rawToken", rawToken
            );
            restTemplate.postForEntity(
                    notificationServiceUrl + "/api/password-reset/create",
                    createRequest,
                    Void.class
            );
        } catch (HttpClientErrorException.NotFound e) {
            log.info("Password reset requested for unknown email, ignoring");
        } catch (Exception e) {
            log.error("Unexpected error while processing forgot-password request", e);
        }
    }

    public void reset(String token, String newPassword) {
        PasswordResetConsumeResponse consumeResponse = restTemplate.postForObject(
                notificationServiceUrl + "/api/password-reset/consume?token=" + token,
                null,
                PasswordResetConsumeResponse.class
        );

        if (consumeResponse == null || !consumeResponse.valid()) {
            throw new InvalidResetTokenException("Ссылка недействительна или устарела");
        }

        String passwordHash = passwordEncoder.encode(newPassword);
        HttpEntity<Map<String, String>> updateRequest =
                new HttpEntity<>(Map.of("passwordHash", passwordHash), internalHeaders());

        String path = ACCOUNT_TYPE_LEGAL.equals(consumeResponse.accountType())
                ? "/api/v1/legal-entities/" + consumeResponse.accountId() + "/password"
                : "/v1/users/" + consumeResponse.accountId() + "/password";

        restTemplate.exchange(userServiceUrl + path, HttpMethod.PUT, updateRequest, Void.class);
    }

    public boolean validate(String token) {
        Map<?, ?> response = restTemplate.getForObject(
                notificationServiceUrl + "/api/password-reset/validate?token=" + token,
                Map.class
        );
        return response != null && Boolean.TRUE.equals(response.get("valid"));
    }

    private String generateRawToken() {
        byte[] bytes = new byte[TOKEN_BYTES_LENGTH];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private HttpHeaders internalHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Internal-Token", internalSecret);
        return headers;
    }
}
