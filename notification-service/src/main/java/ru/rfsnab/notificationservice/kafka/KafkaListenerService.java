package ru.rfsnab.notificationservice.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import ru.rfsnab.notificationservice.handler.NotificationRouter;

/**
 * Единая точка входа для всех Kafka событий.
 * Десериализует JSON, извлекает eventType и делегирует в NotificationRouter.
 * Добавление нового топика — только в аннотацию @KafkaListener.topics.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KafkaListenerService {

    private final NotificationRouter router;
    private final ObjectMapper objectMapper;

    @KafkaListener(
            topics = {"${app.kafka.topic.user-events}", "${app.kafka.topic.order-events}"},
            groupId = "${spring.kafka.consumer.group-id}"
    )
    public void onMessage(ConsumerRecord<String, String> record){
        String topic = record.topic();
        String json = record.value();

        try{
            JsonNode jsonNode = objectMapper.readTree(json);
            String eventType = jsonNode.get("eventType").asText();

            log.info("Kafka event: topic={}, eventType={}, key={}", topic, eventType, record.key());

            router.route(topic, eventType, json);
        } catch (Exception e){
            log.error("Ошибка обработки Kafka события: topic={}, value={}",
                    topic, json, e);
        }
    }
}
