package ru.rfsnab.notificationservice.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import ru.rfsnab.notificationservice.models.ImportEvent;
import ru.rfsnab.notificationservice.service.EmailService;

/**
 * Обработчик событий импорта каталога (топик import-events).
 * Присылает менеджеру отчёт о ночном импорте ФТК — всегда, независимо от статуса.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ImportHandler implements NotificationHandler {

    private final ObjectMapper objectMapper;
    private final EmailService emailService;

    @Value("${app.kafka.topic.import-events}")
    private String topic;

    @Override
    public boolean supports(String topic, String eventType) {
        return this.topic.equals(topic) && "FTK_IMPORT_COMPLETED".equals(eventType);
    }

    @Override
    public void handle(String eventJson) {
        try {
            ImportEvent event = objectMapper.readValue(eventJson, ImportEvent.class);
            emailService.sendImportReportEmail(event);
            log.info("Import report email sent: status={}, failed={}", event.status(), event.failed());
        } catch (Exception e) {
            log.error("Ошибка обработки события импорта: {}", eventJson, e);
        }
    }
}
