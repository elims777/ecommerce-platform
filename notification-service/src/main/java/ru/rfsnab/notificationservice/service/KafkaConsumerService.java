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

    @KafkaListener(
            topics = "${app.kafka.topic.user-events}",
            groupId = "${spring.kafka.consumer.group-id}"
    )
    public void consumeUserEvent(UserEvent event){
        log.info("Received event from Kafka: type={}, userId={}, email={}",
                event.getEventType(), event.getId(), event.getEmail());

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

        LocalDateTime now = LocalDateTime.now();
        EmailVerificationToken token = EmailVerificationToken.builder()
                .token(event.getVerificationToken())
                .userId(event.getId())
                .email(event.getEmail())
                .createdAt(now)
                .expiresAt(now.plusHours(1))
                .verified(false)
                .build();

        tokenService.save(token);
        log.info("Verification token saved for userId: {}", event.getId());

        try{
            emailService.sendVerificationEmail(
                    event.getEmail(), event.getFirsName(), event.getVerificationToken()
            );
            log.info("Verification email send to: {}", event.getEmail());
        } catch (Exception e){
            log.error("Failed to send email to: {}", event.getEmail());
            throw new RuntimeException("Email sending failed", e);
        }

    }
}
