package ru.rfsnab.notificationservice.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import ru.rfsnab.notificationservice.service.EmailService;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class InactivityEmailScheduler {

    private final EmailService emailService;
    private final RestTemplate restTemplate;

    @Value("${app.user-service.url:http://localhost:8081}")
    private String userServiceUrl;

    @Value("${app.frontend-url:http://localhost:5173}")
    private String frontendUrl;

    @Scheduled(cron = "0 0 10 * * *")
    public void sendInactivityEmails() {
        log.info("Запуск задачи рассылки писем о неактивности");

        try {
            ResponseEntity<List<Map<String, Object>>> response = restTemplate.exchange(
                    userServiceUrl + "/v1/users/inactive",
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<>() {}
            );

            List<Map<String, Object>> users = response.getBody();
            if (users == null || users.isEmpty()) {
                log.info("Неактивных пользователей не найдено");
                return;
            }

            log.info("Найдено неактивных пользователей: {}", users.size());

            for (Map<String, Object> user : users) {
                try {
                    Long userId = ((Number) user.get("id")).longValue();
                    String email = (String) user.get("email");
                    String firstname = (String) user.get("firstname");
                    String unsubscribeToken = (String) user.get("unsubscribeToken");

                    String unsubscribeUrl = userServiceUrl + "/v1/users/unsubscribe?token=" + unsubscribeToken;
                    String catalogUrl = frontendUrl + "/catalog";

                    emailService.sendInactivityEmail(email, firstname, catalogUrl, unsubscribeUrl);

                    restTemplate.postForEntity(
                            userServiceUrl + "/v1/users/" + userId + "/inactivity-email-sent",
                            null, Void.class
                    );

                    log.info("Письмо о неактивности отправлено: userId={}, email={}", userId, email);
                } catch (Exception e) {
                    log.error("Ошибка отправки письма пользователю {}: {}", user.get("id"), e.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("Ошибка выполнения задачи рассылки: {}", e.getMessage(), e);
        }
    }
}
