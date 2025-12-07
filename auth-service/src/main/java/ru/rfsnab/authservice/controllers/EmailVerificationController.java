package ru.rfsnab.authservice.controllers;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;
import ru.rfsnab.authservice.models.dto.VerificationResponse;

@Slf4j
@RestController
@RequiredArgsConstructor
public class EmailVerificationController {

    private final RestTemplate restTemplate;

    @Value("${notification.service.url}")
    private String notificationServiceUrl;

    @Value("${notification.service.verification}")
    private String notificationVerifyUrl;

    @Value("${user.service.verify}")
    private String userServiceVerifyUrl;


    @GetMapping("/verify-email")
    public ResponseEntity<String> verifyEmail(@RequestParam String token){
        log.info("Received verification request for token: {}", token);

        try {
            // 1. Проверяем токен в notification-service
            String notificationUrl = notificationServiceUrl+ notificationVerifyUrl + token;
            ResponseEntity<VerificationResponse> verificationResponse =
                    restTemplate.postForEntity(notificationUrl, null, VerificationResponse.class);

            VerificationResponse body = verificationResponse.getBody();
            if (body == null) {
                log.error("Notification service returned empty response");
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body("Ошибка при проверке токена");
            }

            if (!body.isValid()) {
                return ResponseEntity.badRequest()
                        .body("Verification failed: " + body.getMessage());
            }

            // 2. Обновляем статус пользователя в user-service
            Long userId = verificationResponse.getBody().getUserId();
            String userServiceUrl = userServiceVerifyUrl + userId + "/verify";
            restTemplate.put(userServiceUrl, null);

            log.info("Email verified successfully for user: {}", userId);

            return ResponseEntity.ok("Email успешно подтвержден! Теперь вы можете пользоваться сервисом.");

        } catch (Exception e) {
            log.error("Error during email verification", e);
            return ResponseEntity.internalServerError()
                    .body("Verification failed. Please try again later.");
        }
    }
}
