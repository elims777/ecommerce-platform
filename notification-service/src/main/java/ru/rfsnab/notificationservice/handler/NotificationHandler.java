package ru.rfsnab.notificationservice.handler;

/**
 * Единый интерфейс для обработчиков уведомлений.
 * Каждый handler определяет, какие события обрабатывает через supports().
 * Spring автоматически подхватывает все @Component реализации.
 */
public interface NotificationHandler {
    /**
     * Определяет, может ли handler обработать событие.
     *
     * @param topic     Kafka topic
     * @param eventType тип события из JSON
     */
    boolean supports(String topic, String eventType);

    /** Обработка сырого JSON события */
    void handle(String eventJson);
}
