package ru.rfsnab.notificationservice.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import ru.rfsnab.notificationservice.models.CarrierSyncEvent;
import ru.rfsnab.notificationservice.service.EmailService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("CarrierSyncHandler unit tests")
class CarrierSyncHandlerTest {

    private static final String TOPIC = "order-events";

    @Mock
    private EmailService emailService;

    private CarrierSyncHandler handler;

    @BeforeEach
    void setUp() {
        handler = new CarrierSyncHandler(new ObjectMapper(), emailService);
        ReflectionTestUtils.setField(handler, "topic", TOPIC);
    }

    @Test
    @DisplayName("supports: только order-events + CARRIER_SYNC_COMPLETED")
    void supports_OnlyCarrierSyncOnOrderTopic() {
        assertThat(handler.supports(TOPIC, "CARRIER_SYNC_COMPLETED")).isTrue();
        assertThat(handler.supports(TOPIC, "ORDER_CREATED")).isFalse();
        assertThat(handler.supports("import-events", "CARRIER_SYNC_COMPLETED")).isFalse();
    }

    @Test
    @DisplayName("handle: JSON разбирается и уходит в EmailService")
    void handle_ValidJson_SendsReportEmail() {
        String json = """
                {"eventType":"CARRIER_SYNC_COMPLETED","trigger":"MANUAL","reports":[
                 {"carrierCode":"DELLIN","carrierName":"Деловые линии","status":"WARNING",
                  "received":10,"added":1,"updated":2,"deactivated":3,"skipped":1,
                  "skippedItems":[{"externalId":"7","reason":"нет координат"}],
                  "moreSkipped":0,"message":null,"durationMs":1500,"finishedAt":"2026-10-12T03:00:00Z"}]}
                """;

        handler.handle(json);

        ArgumentCaptor<CarrierSyncEvent> captor = ArgumentCaptor.forClass(CarrierSyncEvent.class);
        verify(emailService).sendCarrierSyncReportEmail(captor.capture());
        assertThat(captor.getValue().trigger()).isEqualTo("MANUAL");
        assertThat(captor.getValue().reports()).hasSize(1);
        assertThat(captor.getValue().reports().get(0).skippedItems())
                .containsExactly(new CarrierSyncEvent.SkippedItem("7", "нет координат"));
    }

    @Test
    @DisplayName("handle: битый JSON не бросает исключение и письмо не уходит")
    void handle_InvalidJson_NoEmail() {
        handler.handle("not-json");

        verify(emailService, never()).sendCarrierSyncReportEmail(any());
    }
}
