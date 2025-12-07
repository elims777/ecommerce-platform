package ru.rfsnab.userservice.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import ru.rfsnab.userservice.models.kafka.UserEvent;

@Slf4j
@Service
@RequiredArgsConstructor
public class KafkaProducerService {
    private final KafkaTemplate<String, UserEvent> kafkaTemplate;
    @Value("${app.kafka.topic.user-events}")
    private String userEventsTopic;

    public void sendUserRegisteredEvent(UserEvent event){
        log.info("Sending USER_REGISTERED event to Kafka: userId={}, email={}",
                event.getUserId(), event.getEmail());
        try{
            kafkaTemplate.send(userEventsTopic, event.getUserId().toString(), event);
            log.info("Event sent successfully to topic: {}", userEventsTopic);
        } catch (Exception e){
            log.error("Failed to send event to Kafka", e);
            throw new RuntimeException("Failed to send Kafka event", e);
        }
    }
}
