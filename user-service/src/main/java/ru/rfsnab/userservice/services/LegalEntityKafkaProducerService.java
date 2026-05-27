package ru.rfsnab.userservice.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import ru.rfsnab.userservice.models.kafka.LegalEntityEvent;

/**
 * Kafka-продюсер для событий жизненного цикла юридического лица.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LegalEntityKafkaProducerService {

    private final KafkaTemplate<String, LegalEntityEvent> legalEntityKafkaTemplate;

    @Value("${app.kafka.topic.legal-entity-events}")
    private String topic;

    public void send(LegalEntityEvent event) {
        legalEntityKafkaTemplate.send(topic, event.legalEntityId().toString(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to send LegalEntityEvent: {}", event.eventType(), ex);
                    }
                });
    }
}
