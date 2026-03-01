package ru.rfsnab.notificationservice.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import ru.rfsnab.notificationservice.models.EmailVerificationToken;
import ru.rfsnab.notificationservice.models.UserEvent;
import ru.rfsnab.notificationservice.service.EmailService;
import ru.rfsnab.notificationservice.service.EmailVerificationTokenService;

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserRegisteredHandler implements NotificationHandler{

    private final ObjectMapper objectMapper;
    private final EmailVerificationTokenService tokenService;
    private final EmailService emailService;

    @Value("${app.kafka.topic.user-events}")
    private String topic;


    @Override
    public boolean supports(String topic, String eventType) {
        return this.topic.equals(topic) && "USER_REGISTERED".equals(eventType);
    }

    @Override
    public void handle(String eventJson) {
        try{
            UserEvent event = objectMapper.readValue(eventJson, UserEvent.class);

            // Idempotency — проверка дубликата
            if(tokenService.existsByToken(event.getVerificationToken())){
                log.warn("Дубликат verification token: {}", event.getVerificationToken());
                return;
            }
            // Сохраняем токен в БД
            EmailVerificationToken token = EmailVerificationToken.builder()
                    .token(event.getVerificationToken())
                    .userId(event.getUserId())
                    .email(event.getEmail())
                    .createdAt(LocalDateTime.now())
                    .expiresAt(LocalDateTime.now().plusHours(1))
                    .build();

            tokenService.save(token);

            // Отправляем email
            emailService.sendVerificationEmail(
                    event.getEmail(),
                    event.getFirstName(),
                    event.getVerificationToken()
            );

            log.info("Verification email отправлен: userId={}, email={}",
                    event.getUserId(), event.getEmail());

        } catch (Exception e) {
            log.error("Ошибка обработки USER_REGISTERED: {}", eventJson, e);
        }
    }
}
