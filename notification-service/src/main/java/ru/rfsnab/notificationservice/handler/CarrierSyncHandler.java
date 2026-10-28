package ru.rfsnab.notificationservice.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import ru.rfsnab.notificationservice.models.CarrierSyncEvent;
import ru.rfsnab.notificationservice.service.EmailService;

/**
 * Обработчик события CARRIER_SYNC_COMPLETED (топик order-events).
 * Присылает менеджеру отчёт о синхронизации терминалов ТК — после каждого запуска.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CarrierSyncHandler implements NotificationHandler {

    /** Тип события синхронизации терминалов ТК. */
    public static final String EVENT_TYPE = "CARRIER_SYNC_COMPLETED";

    private final ObjectMapper objectMapper;
    private final EmailService emailService;

    @Value("${app.kafka.topic.order-events}")
    private String topic;

    @Override
    public boolean supports(String topic, String eventType) {
        return this.topic.equals(topic) && EVENT_TYPE.equals(eventType);
    }

    @Override
    public void handle(String eventJson) {
        try {
            CarrierSyncEvent event = objectMapper.readValue(eventJson, CarrierSyncEvent.class);
            emailService.sendCarrierSyncReportEmail(event);
            log.info("Carrier sync report email sent: trigger={}, carriers={}", event.trigger(),
                    event.reports() == null ? 0 : event.reports().size());
        } catch (Exception e) {
            log.error("Ошибка обработки события синхронизации ТК", e);
        }
    }
}
