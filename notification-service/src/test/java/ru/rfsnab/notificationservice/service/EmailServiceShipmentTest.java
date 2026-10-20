package ru.rfsnab.notificationservice.service;

import jakarta.mail.Message;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import ru.rfsnab.notificationservice.models.OrderEvent;
import ru.rfsnab.notificationservice.models.OrderEvent.ShipmentLine;
import ru.rfsnab.notificationservice.models.ShipmentNotFoundEvent;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("EmailService — отправления и накладная не найдена (настоящие шаблоны)")
class EmailServiceShipmentTest {

    private static final String MANAGER_EMAIL = "manager@rfsnab.ru";
    private static final String STATUS_TEMPLATE = "mail/order-status-changed";
    private static final String BLOCK_TITLE = "Доставка / Отправления";

    @Mock
    private JavaMailSender mailSender;

    private SpringTemplateEngine engine;
    private EmailService emailService;

    @BeforeEach
    void setUp() {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setCharacterEncoding("UTF-8");
        engine = Mockito.spy(new SpringTemplateEngine());
        engine.setTemplateResolver(resolver);

        emailService = new EmailService(mailSender, engine);
        ReflectionTestUtils.setField(emailService, "fromEmail", "noreply@rfsnab.ru");
        ReflectionTestUtils.setField(emailService, "managerEmail", MANAGER_EMAIL);
        ReflectionTestUtils.setField(emailService, "frontendUrl", "https://rfsnab.ru");
        lenient().when(mailSender.createMimeMessage()).thenReturn(new MimeMessage((Session) null));
    }

    private OrderEvent statusEvent(String status, String carrier, String terminal, List<ShipmentLine> shipments) {
        return new OrderEvent("ORDER_STATUS_CHANGED", UUID.randomUUID(), "ORD-7", 1L, status,
                new BigDecimal("100.00"), "customer@example.com", "Иван", "+79001234567",
                LocalDateTime.now(), List.of(), "CARRIER_TERMINAL", "CARD", null, null, "B2C",
                null, null, null, null, null, carrier, terminal, terminal == null ? null : "ул. Складская, 5",
                shipments);
    }

    private String renderStatus(OrderEvent event) {
        emailService.sendOrderStatusChangedEmail(event);
        ArgumentCaptor<Context> ctx = ArgumentCaptor.forClass(Context.class);
        verify(engine).process(eq(STATUS_TEMPLATE), ctx.capture());
        return engine.process(STATUS_TEMPLATE, ctx.getValue());
    }

    @Test
    @DisplayName("статус «Прибыл на терминал»: блок с ТК, терминалом, отправлениями и ссылкой «Отследить» только где есть trackingUrl")
    void statusEmail_WithShipments_RendersBlock() {
        String html = renderStatus(statusEvent("Прибыл на терминал", "Деловые Линии", "Терминал Север",
                List.of(new ShipmentLine("Деловые Линии", "23-0001", "https://dl.ru/track/23-0001", "Прибыл"),
                        new ShipmentLine("СДЭК", "555", null, "В пути"))));

        assertThat(html).contains("Прибыл на терминал", BLOCK_TITLE, "Доставка до терминала (ТК)",
                "Деловые Линии", "Терминал Север", "ул. Складская, 5", "23-0001", "Прибыл", "555", "В пути",
                "https://dl.ru/track/23-0001");
        assertThat(html.split("Отследить", -1)).hasSize(2);
    }

    @Test
    @DisplayName("статус «Доставлен» без данных о доставке (shipments = null): блок скрыт")
    void statusEmail_NoData_HidesBlock() {
        String html = renderStatus(statusEvent("Доставлен", null, null, null));

        assertThat(html).contains("Доставлен").doesNotContain(BLOCK_TITLE);
    }

    @Test
    @DisplayName("статус вне списка доставки: блок скрыт даже при наличии ТК")
    void statusEmail_NonShipmentStatus_HidesBlock() {
        String html = renderStatus(statusEvent("В работе", "СДЭК", null, List.of()));

        assertThat(html).doesNotContain(BLOCK_TITLE);
    }

    @Test
    @DisplayName("shipment-not-found: письмо менеджеру с номером накладной, ТК и заказом")
    void shipmentNotFound_RendersAndGoesToManager() throws Exception {
        emailService.sendShipmentNotFoundEmail(new ShipmentNotFoundEvent(
                "SHIPMENT_NOT_FOUND", "ORD-7", "СДЭК", "123456", "2026-10-12T03:00:00Z"));

        ArgumentCaptor<MimeMessage> msg = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(msg.capture());
        assertThat(msg.getValue().getRecipients(Message.RecipientType.TO)[0].toString()).isEqualTo(MANAGER_EMAIL);
        assertThat(msg.getValue().getSubject()).contains("123456", "СДЭК", "ORD-7");

        ArgumentCaptor<Context> ctx = ArgumentCaptor.forClass(Context.class);
        verify(engine).process(eq("mail/shipment-not-found"), ctx.capture());
        String html = engine.process("mail/shipment-not-found", ctx.getValue());
        assertThat(html).contains("123456", "СДЭК", "ORD-7", "проверьте номер", "12.10.2026 06:00");
        assertThat(html).doesNotContain("2026-10-12T03:00:00Z");
    }

    @Test
    @DisplayName("shipment-not-found: нераспознанная строка времени показывается как есть")
    void shipmentNotFound_InvalidTimeShownAsIs() throws Exception {
        emailService.sendShipmentNotFoundEmail(new ShipmentNotFoundEvent(
                "SHIPMENT_NOT_FOUND", "ORD-7", "СДЭК", "123456", "вчера вечером"));

        ArgumentCaptor<Context> ctx = ArgumentCaptor.forClass(Context.class);
        verify(engine).process(eq("mail/shipment-not-found"), ctx.capture());
        assertThat(engine.process("mail/shipment-not-found", ctx.getValue())).contains("вчера вечером");
    }
}
