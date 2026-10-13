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
import ru.rfsnab.notificationservice.models.CarrierSyncEvent;
import ru.rfsnab.notificationservice.models.CarrierSyncEvent.CarrierSyncReport;
import ru.rfsnab.notificationservice.models.CarrierSyncEvent.SkippedItem;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("EmailService — отчёт о синхронизации терминалов ТК")
class EmailServiceCarrierSyncTest {

    private static final String MANAGER_EMAIL = "manager@rfsnab.ru";
    private static final String TEMPLATE = "mail/carrier-sync-report";

    @Mock
    private JavaMailSender mailSender;

    private SpringTemplateEngine engine;
    private EmailService emailService;

    @BeforeEach
    void setUp() {
        // Настоящий движок и настоящие шаблоны: проверяем, что carrier-sync-report реально рендерится.
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setCharacterEncoding("UTF-8");
        engine = Mockito.spy(new SpringTemplateEngine());
        engine.setTemplateResolver(resolver);

        emailService = new EmailService(mailSender, engine);
        ReflectionTestUtils.setField(emailService, "fromEmail", "noreply@rfsnab.ru");
        ReflectionTestUtils.setField(emailService, "managerEmail", MANAGER_EMAIL);
        lenient().when(mailSender.createMimeMessage()).thenReturn(new MimeMessage((Session) null));
    }

    private CarrierSyncReport report(String name, String status, List<SkippedItem> items, int more, String message) {
        return new CarrierSyncReport("CODE", name, status, 100, 5, 6, 7, items.size() + more,
                items, more, message, 2500, "2026-10-12T03:00:00Z");
    }

    private CarrierSyncEvent event(String trigger, CarrierSyncReport... reports) {
        return new CarrierSyncEvent("CARRIER_SYNC_COMPLETED", trigger, List.of(reports));
    }

    @Test
    @DisplayName("тема по худшему статусу: ERROR > WARNING > OK")
    void subject_WorstStatusWins() {
        List<SkippedItem> none = List.of();
        assertThat(emailService.carrierSyncSubject(event("MANUAL", report("A", "OK", none, 0, null))))
                .isEqualTo("Синхронизация терминалов ТК: OK");
        assertThat(emailService.carrierSyncSubject(event("MANUAL",
                report("A", "OK", none, 0, null), report("B", "WARNING", none, 0, null))))
                .isEqualTo("Синхронизация терминалов ТК: есть предупреждения");
        assertThat(emailService.carrierSyncSubject(event("MANUAL",
                report("A", "WARNING", none, 0, null), report("B", "ERROR", none, 0, "boom"))))
                .isEqualTo("Синхронизация терминалов ТК: ошибки");
    }

    @Test
    @DisplayName("письмо уходит на MANAGER_EMAIL с темой по худшему статусу")
    void send_GoesToManagerWithSubject() throws Exception {
        emailService.sendCarrierSyncReportEmail(event("SCHEDULED", report("СДЭК", "ERROR", List.of(), 0, "401")));

        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(captor.capture());
        assertThat(captor.getValue().getRecipients(Message.RecipientType.TO)[0].toString()).isEqualTo(MANAGER_EMAIL);
        assertThat(captor.getValue().getSubject()).isEqualTo("Синхронизация терминалов ТК: ошибки");
    }

    @Test
    @DisplayName("шаблон: таблица ТК, пропуски с причинами, 'и ещё X', trigger по-русски, сообщение")
    void template_RendersSkippedAndMore() {
        emailService.sendCarrierSyncReportEmail(event("SCHEDULED",
                report("Деловые линии", "WARNING", List.of(new SkippedItem("ID-77", "нет координат")), 12, "частичный ответ"),
                report("ПЭК", "OK", List.of(), 0, null)));

        ArgumentCaptor<Context> ctx = ArgumentCaptor.forClass(Context.class);
        verify(engine).process(eq(TEMPLATE), ctx.capture());
        String html = engine.process(TEMPLATE, ctx.getValue());

        assertThat(html).contains("Деловые линии", "ПЭК", "Предупреждение", "ночная",
                "ID-77", "нет координат", "и ещё", "12", "частичный ответ");
        assertThat(html).doesNotContain("Пропущенные записи: ПЭК");
    }
}
