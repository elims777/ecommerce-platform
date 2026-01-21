package ru.rfsnab.notificationservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import ru.rfsnab.notificationservice.models.EmailVerificationToken;
import ru.rfsnab.notificationservice.models.UserEvent;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class KafkaConsumerService {
    private final EmailVerificationTokenService tokenService;
    private final EmailService emailService;
    private final EmailVerificationTokenService emailVerificationTokenService;

    @KafkaListener(
            topics = "${app.kafka.topic.user-events}",
            groupId = "${spring.kafka.consumer.group-id}"
    )
    public void consumeUserEvent(UserEvent event){
        log.info("Received event from Kafka: type={}, userId={}, email={}",
                event.getEventType(), event.getUserId(), event.getEmail());

        try{
            if("USER_REGISTERED".equals(event.getEventType())){
                handleUserRegistrationEvent(event);
            } else {
                log.warn("Unknown event type: {}", event.getEventType());
            }
        } catch(Exception e){
            log.error("Failed to process event: {}", event, e);
            throw e;
        }
    }

    private void handleUserRegistrationEvent(UserEvent event){
        log.info("Processing user registration for: {}", event.getEmail());

        if (emailVerificationTokenService.existsByToken(event.getVerificationToken())) {
            log.info("Token already exists, skipping: {}", event.getVerificationToken());
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        EmailVerificationToken token = EmailVerificationToken.builder()
                .token(event.getVerificationToken())
                .userId(event.getUserId())
                .email(event.getEmail())
                .createdAt(now)
                .expiresAt(now.plusHours(1))
                .verified(false)
                .build();

        tokenService.save(token);
        log.info("Verification token saved for userId: {}", event.getUserId());

        try{
            emailService.sendVerificationEmail(
                    event.getEmail(), event.getFirstName(), event.getVerificationToken()
            );
            log.info("Verification email send to: {}", event.getEmail());
        } catch (Exception e){
            log.error("Failed to send email to: {}", event.getEmail());
            throw new RuntimeException("Email sending failed", e);
        }

    }
}
