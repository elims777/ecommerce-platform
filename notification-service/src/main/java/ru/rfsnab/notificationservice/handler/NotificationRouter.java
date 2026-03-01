package ru.rfsnab.notificationservice.handler;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Роутер уведомлений — находит подходящий handler по topic + eventType.
 * Handler'ы подхватываются автоматически через Spring DI.
 * Добавление нового handler'а не требует изменений в роутере (Open-Closed Principle).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationRouter {

    private final List<NotificationHandler> handlers;

    @PostConstruct
    void init(){
        log.info("Зарегистрировано {} notification handler(s): {}",
                handlers.size(),
                handlers.stream()
                        .map(h -> h.getClass().getSimpleName())
                        .toList());
    }

    /**
     * Роутинг события к нужному handler'у.
     *
     * @param topic     Kafka topic
     * @param eventType тип события из JSON
     * @param eventJson сырой JSON
     */
    public void route(String topic, String eventType, String eventJson) {
        handlers.stream()
                .filter(h -> h.supports(topic, eventType))
                .findFirst()
                .ifPresentOrElse(
                        handler -> {
                            log.debug("Роутинг {}:{} -> {}", topic, eventType,
                                    handler.getClass().getSimpleName());
                            handler.handle(eventJson);
                        },
                        () -> log.warn("Нет handler'а для topic={}, eventType={}", topic, eventType)
                );
    }
}
