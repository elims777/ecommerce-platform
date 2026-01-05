package ru.rfsnab.userservice.services;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import ru.rfsnab.userservice.models.kafka.UserEvent;

@Service
@RequiredArgsConstructor
public class KafkaProducerService {
    private final KafkaTemplate<String, UserEvent> kafkaTemplate;
    @Value("${app.kafka.topic.user-events}")
    private String userEventsTopic;

    public void sendUserRegisteredEvent(UserEvent event){
        try{
            kafkaTemplate.send(userEventsTopic, event.getUserId().toString(), event);
        } catch (Exception e){
            throw new RuntimeException("Failed to send Kafka event", e);
        }
    }
}
