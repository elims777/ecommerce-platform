package ru.rfsnab.notificationservice.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import ru.rfsnab.notificationservice.models.ShipmentNotFoundEvent;
import ru.rfsnab.notificationservice.service.EmailService;

/**
 * Обработчик события SHIPMENT_NOT_FOUND (топик order-events).
 * Письмо менеджеру: накладная ТК не найдена, нужно проверить номер.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ShipmentNotFoundHandler implements NotificationHandler {

    /** Тип события «накладная не найдена». */
    public static final String EVENT_TYPE = "SHIPMENT_NOT_FOUND";

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
            ShipmentNotFoundEvent event = objectMapper.readValue(eventJson, ShipmentNotFoundEvent.class);
            emailService.sendShipmentNotFoundEmail(event);
            log.info("Shipment not found email sent: order={}, carrier={}", event.orderNumber(), event.carrierName());
        } catch (Exception e) {
            log.error("Ошибка обработки события SHIPMENT_NOT_FOUND", e);
        }
    }
}
