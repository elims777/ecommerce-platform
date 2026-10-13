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
import ru.rfsnab.notificationservice.models.ShipmentNotFoundEvent;
import ru.rfsnab.notificationservice.service.EmailService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("ShipmentNotFoundHandler unit tests")
class ShipmentNotFoundHandlerTest {

    private static final String TOPIC = "order-events";

    @Mock
    private EmailService emailService;

    private ShipmentNotFoundHandler handler;

    @BeforeEach
    void setUp() {
        handler = new ShipmentNotFoundHandler(new ObjectMapper(), emailService);
        ReflectionTestUtils.setField(handler, "topic", TOPIC);
    }

    @Test
    @DisplayName("supports: только order-events + SHIPMENT_NOT_FOUND")
    void supports_OnlyShipmentNotFoundOnOrderTopic() {
        assertThat(handler.supports(TOPIC, "SHIPMENT_NOT_FOUND")).isTrue();
        assertThat(handler.supports(TOPIC, "ORDER_CREATED")).isFalse();
        assertThat(handler.supports("import-events", "SHIPMENT_NOT_FOUND")).isFalse();
    }

    @Test
    @DisplayName("handle: JSON разбирается и уходит в EmailService")
    void handle_ValidJson_SendsEmail() {
        handler.handle("""
                {"eventType":"SHIPMENT_NOT_FOUND","orderNumber":"ORD-1","carrierName":"СДЭК",
                 "waybillNumber":"123456","notFoundSince":"2026-10-12T03:00:00Z","extra":1}
                """);

        ArgumentCaptor<ShipmentNotFoundEvent> captor = ArgumentCaptor.forClass(ShipmentNotFoundEvent.class);
        verify(emailService).sendShipmentNotFoundEmail(captor.capture());
        assertThat(captor.getValue().orderNumber()).isEqualTo("ORD-1");
        assertThat(captor.getValue().carrierName()).isEqualTo("СДЭК");
        assertThat(captor.getValue().waybillNumber()).isEqualTo("123456");
    }

    @Test
    @DisplayName("handle: битый JSON не бросает исключение и письмо не уходит")
    void handle_InvalidJson_NoEmail() {
        handler.handle("not-json");

        verify(emailService, never()).sendShipmentNotFoundEmail(any());
    }
}
