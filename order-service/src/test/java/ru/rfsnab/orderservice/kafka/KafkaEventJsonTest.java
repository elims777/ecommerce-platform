package ru.rfsnab.orderservice.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.rfsnab.orderservice.config.KafkaConfig;
import ru.rfsnab.orderservice.delivery.CarrierCode;
import ru.rfsnab.orderservice.delivery.CarrierSyncEvent;
import ru.rfsnab.orderservice.delivery.CarrierSyncReport;
import ru.rfsnab.orderservice.delivery.ShipmentNotFoundEvent;
import ru.rfsnab.orderservice.delivery.SyncStatus;
import ru.rfsnab.orderservice.delivery.SyncTrigger;
import ru.rfsnab.orderservice.models.dto.event.OrderEvent;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Контракт JSON событий для notification-service: имена полей и даты ISO-строками.
 * Сериализует тот самый ObjectMapper, который KafkaConfig отдаёт в JsonSerializer продюсера.
 */
@DisplayName("Kafka events: JSON через kafkaObjectMapper")
class KafkaEventJsonTest {

    private final ObjectMapper mapper = new KafkaConfig().kafkaObjectMapper();

    private JsonNode json(Object event) throws Exception {
        return mapper.readTree(mapper.writeValueAsString(event));
    }

    @Test
    @DisplayName("CarrierSyncEvent: eventType, trigger, поля отчёта, finishedAt — ISO-строка")
    void carrierSyncEvent() throws Exception {
        CarrierSyncReport report = new CarrierSyncReport(CarrierCode.DELLIN, "Деловые Линии", SyncStatus.WARNING, 10, 3, 2, 1, 1,
                List.of(new CarrierSyncReport.SkippedItem("X1", "Нет города")), 4, "Подозрительный ответ", 1500,
                Instant.parse("2026-10-12T20:00:01Z"));

        JsonNode node = json(CarrierSyncEvent.of(SyncTrigger.SCHEDULED, List.of(report)));

        assertThat(node.get("eventType").asText()).isEqualTo("CARRIER_SYNC_COMPLETED");
        assertThat(node.get("trigger").asText()).isEqualTo("SCHEDULED");
        JsonNode r = node.get("reports").get(0);
        assertThat(r.get("carrierCode").asText()).isEqualTo("DELLIN");
        assertThat(r.get("carrierName").asText()).isEqualTo("Деловые Линии");
        assertThat(r.get("status").asText()).isEqualTo("WARNING");
        assertThat(r.get("received").asInt()).isEqualTo(10);
        assertThat(r.get("added").asInt()).isEqualTo(3);
        assertThat(r.get("updated").asInt()).isEqualTo(2);
        assertThat(r.get("deactivated").asInt()).isEqualTo(1);
        assertThat(r.get("skipped").asInt()).isEqualTo(1);
        assertThat(r.get("skippedItems").get(0).get("externalId").asText()).isEqualTo("X1");
        assertThat(r.get("skippedItems").get(0).get("reason").asText()).isEqualTo("Нет города");
        assertThat(r.get("moreSkipped").asInt()).isEqualTo(4);
        assertThat(r.get("message").asText()).isEqualTo("Подозрительный ответ");
        assertThat(r.get("durationMs").asLong()).isEqualTo(1500);
        assertThat(r.get("finishedAt").isTextual()).isTrue();
        assertThat(r.get("finishedAt").asText()).isEqualTo("2026-10-12T20:00:01Z");
    }

    @Test
    @DisplayName("ShipmentNotFoundEvent: notFoundSince — ISO-строка")
    void shipmentNotFoundEvent() throws Exception {
        JsonNode node = json(ShipmentNotFoundEvent.of("ABC-1", "ПЭК", "123", Instant.parse("2026-10-11T08:30:00Z")));

        assertThat(node.get("eventType").asText()).isEqualTo("SHIPMENT_NOT_FOUND");
        assertThat(node.get("orderNumber").asText()).isEqualTo("ABC-1");
        assertThat(node.get("carrierName").asText()).isEqualTo("ПЭК");
        assertThat(node.get("waybillNumber").asText()).isEqualTo("123");
        assertThat(node.get("notFoundSince").isTextual()).isTrue();
        assertThat(node.get("notFoundSince").asText()).isEqualTo("2026-10-11T08:30:00Z");
    }

    @Test
    @DisplayName("OrderEvent: shipments, terminalName, terminalAddress, carrierName, timestamp — ISO")
    void orderEventWithShipmentsAndTerminal() throws Exception {
        OrderEvent event = OrderEvent.builder().eventType("ORDER_STATUS_CHANGED").orderNumber("ABC-1")
                .timestamp(LocalDateTime.of(2026, 10, 12, 10, 30, 0))
                .carrierName("Деловые Линии").terminalName("Терминал Толмачёво").terminalAddress("ул. Ленина, 1")
                .shipments(List.of(new OrderEvent.ShipmentLine("ПЭК", "123", "https://pecom.ru/?code=123", "В пути")))
                .build();

        JsonNode node = json(event);

        assertThat(node.get("eventType").asText()).isEqualTo("ORDER_STATUS_CHANGED");
        assertThat(node.get("timestamp").asText()).isEqualTo("2026-10-12T10:30:00");
        assertThat(node.get("carrierName").asText()).isEqualTo("Деловые Линии");
        assertThat(node.get("terminalName").asText()).isEqualTo("Терминал Толмачёво");
        assertThat(node.get("terminalAddress").asText()).isEqualTo("ул. Ленина, 1");
        JsonNode s = node.get("shipments").get(0);
        assertThat(s.get("carrierName").asText()).isEqualTo("ПЭК");
        assertThat(s.get("waybillNumber").asText()).isEqualTo("123");
        assertThat(s.get("trackingUrl").asText()).isEqualTo("https://pecom.ru/?code=123");
        assertThat(s.get("carrierStatus").asText()).isEqualTo("В пути");
    }
}
