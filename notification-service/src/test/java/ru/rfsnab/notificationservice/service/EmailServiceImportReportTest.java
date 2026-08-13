package ru.rfsnab.notificationservice.service;

import jakarta.mail.BodyPart;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import ru.rfsnab.notificationservice.models.ImportEvent;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("EmailService — отчёт об импорте ФТК")
class EmailServiceImportReportTest {

    @Mock
    private JavaMailSender mailSender;

    @Mock
    private TemplateEngine templateEngine;

    @InjectMocks
    private EmailService emailService;

    private static final String FROM_EMAIL = "noreply@rfsnab.ru";
    private static final String MANAGER_EMAIL = "manager@rfsnab.ru";

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(emailService, "fromEmail", FROM_EMAIL);
        ReflectionTestUtils.setField(emailService, "managerEmail", MANAGER_EMAIL);

        lenient().when(mailSender.createMimeMessage()).thenReturn(new MimeMessage((Session) null));
        lenient().when(templateEngine.process(anyString(), any(Context.class))).thenReturn("<html>OK</html>");
    }

    private ImportEvent createEvent(String status, int failed, List<ImportEvent.ImportError> errors,
                                     String errorStacktrace) {
        return createEvent(status, failed, errors, errorStacktrace, null);
    }

    private ImportEvent createEvent(String status, int failed, List<ImportEvent.ImportError> errors,
                                     String errorStacktrace, String rootCause) {
        return new ImportEvent(
                "FTK_IMPORT_COMPLETED",
                status,
                0, 0, 0, 0, failed,
                0, 0,
                60_000L,
                LocalDateTime.of(2026, 8, 13, 3, 0),
                0,
                errors,
                errorStacktrace,
                rootCause
        );
    }

    @Nested
    @DisplayName("importSubject()")
    class ImportSubjectTests {

        @Test
        @DisplayName("status=FAILED, failed=0 -> тема содержит ОШИБКА (регресс-тест провала на первом шаге)")
        void failedStatus_ZeroFailedCount_SubjectContainsError() {
            ImportEvent event = createEvent("FAILED", 0, List.of(), "stacktrace");

            String subject = emailService.importSubject(event);

            assertThat(subject).contains("ОШИБКА");
        }

        @Test
        @DisplayName("status=PARTIAL, failed=7 -> тема содержит '7 ошибок'")
        void partialStatus_SevenFailed_SubjectContainsCount() {
            ImportEvent event = createEvent("PARTIAL", 7, List.of(), null);

            String subject = emailService.importSubject(event);

            assertThat(subject).contains("7 ошибок");
        }

        @Test
        @DisplayName("status=PARTIAL, failed=0, упали только картинки -> тема НЕ говорит 'успешно'")
        void partialStatus_OnlyImagesFailed_SubjectIsNotSuccess() {
            ImportEvent event = new ImportEvent(
                    "FTK_IMPORT_COMPLETED", "PARTIAL",
                    100, 0, 100, 0, 0,
                    50, 12,
                    60_000L, LocalDateTime.of(2026, 8, 13, 3, 0),
                    0, List.of(), null, null);

            String subject = emailService.importSubject(event);

            assertThat(subject).doesNotContain("успешно");
            assertThat(subject).contains("12");
        }

        @Test
        @DisplayName("status=SUCCESS, failed=0 -> тема содержит 'успешно'")
        void successStatus_ZeroFailed_SubjectContainsSuccess() {
            ImportEvent event = createEvent("SUCCESS", 0, List.of(), null);

            String subject = emailService.importSubject(event);

            assertThat(subject).contains("успешно");
        }
    }

    @Nested
    @DisplayName("buildImportReportFile()")
    class BuildImportReportFileTests {

        @Test
        @DisplayName("FAILED со стектрейсом и ошибкой -> содержит причину сбоя, сообщение и стектрейс")
        void failedWithStacktraceAndError_ContainsAllSections() {
            ImportEvent.ImportError error = new ImportEvent.ImportError("FTK-123", "Не удалось распарсить XML", false);
            ImportEvent event = createEvent("FAILED", 0, List.of(error), "java.lang.RuntimeException: boom\n\tat Foo.bar");

            String text = emailService.buildImportReportFile(event);

            assertThat(text).contains("=== ПРИЧИНА СБОЯ ===");
            assertThat(text).contains("Не удалось распарсить XML");
            assertThat(text).contains("=== СТЕКТРЕЙС ===");
            assertThat(text).contains("java.lang.RuntimeException: boom");
        }

        @Test
        @DisplayName("без стектрейса -> секции СТЕКТРЕЙС нет")
        void withoutStacktrace_NoStacktraceSection() {
            ImportEvent event = createEvent("FAILED", 0, List.of(), null);

            String text = emailService.buildImportReportFile(event);

            assertThat(text).doesNotContain("=== СТЕКТРЕЙС ===");
        }

        @Test
        @DisplayName("исключение без message -> вместо 'null' отсылка к стектрейсу, сам стектрейс на месте")
        void errorWithoutMessage_ReferencesStacktraceInsteadOfNull() {
            ImportEvent.ImportError error = new ImportEvent.ImportError(null, null, false);
            ImportEvent event = createEvent("FAILED", 0, List.of(error),
                    "java.lang.NullPointerException\n\tat ru.rfsnab.FtkImportService.doImportFromFtp(FtkImportService.java:124)");

            String text = emailService.buildImportReportFile(event);

            // ни одна строка отчёта не должна быть голым "null"
            assertThat(text.lines()).noneMatch(line -> line.trim().equals("null"));
            assertThat(text).contains("см. стектрейс ниже");
            assertThat(text).contains("=== СТЕКТРЕЙС ===");
            assertThat(text).contains("FtkImportService.java:124");
        }

        @Test
        @DisplayName("есть rootCause -> в отчёте строка 'Первопричина:' с сообщением и местом падения")
        void withRootCause_ContainsRootCauseLine() {
            ImportEvent.ImportError error = new ImportEvent.ImportError(null,
                    "NullPointerException (без сообщения) — FtkImportService.doImportFromFtp (FtkImportService.java:124)", false);
            ImportEvent event = createEvent("FAILED", 0, List.of(error), "stacktrace",
                    "value too long for character varying(255) — ProductImportService.save (ProductImportService.java:210)");

            String text = emailService.buildImportReportFile(event);

            assertThat(text).contains("Первопричина: value too long for character varying(255)");
            assertThat(text).contains("ProductImportService.java:210");
        }

        @Test
        @DisplayName("rootCause отсутствует -> строки 'Первопричина:' нет")
        void withoutRootCause_NoRootCauseLine() {
            ImportEvent.ImportError error = new ImportEvent.ImportError(null, "Корневой import___.xml не найден на FTP", false);
            ImportEvent event = createEvent("FAILED", 0, List.of(error), "stacktrace", null);

            String text = emailService.buildImportReportFile(event);

            assertThat(text).doesNotContain("Первопричина:");
        }

        @Test
        @DisplayName("нет ни message, ни стектрейса -> 'не указана'")
        void noMessageNoStacktrace_ReasonNotSpecified() {
            ImportEvent event = createEvent("FAILED", 0, List.of(), null);

            String text = emailService.buildImportReportFile(event);

            assertThat(text).contains("не указана");
        }
    }

    @Nested
    @DisplayName("sendImportReportEmail()")
    class SendImportReportEmailTests {

        /**
         * Собирает имена вложений реально отправленного MimeMessage.
         * MimeMessageHelper пишет в настоящий jakarta.mail MimeMessage, поэтому
         * структуру письма можно разобрать обратно без embedded SMTP.
         */
        private List<String> attachmentNamesOf(MimeMessage message) throws Exception {
            List<String> names = new ArrayList<>();
            collectAttachmentNames(message.getContent(), names);
            return names;
        }

        private void collectAttachmentNames(Object content, List<String> names) throws Exception {
            if (!(content instanceof Multipart multipart)) {
                return;
            }
            for (int i = 0; i < multipart.getCount(); i++) {
                BodyPart part = multipart.getBodyPart(i);
                if (Part.ATTACHMENT.equalsIgnoreCase(part.getDisposition()) && part.getFileName() != null) {
                    names.add(part.getFileName());
                } else {
                    collectAttachmentNames(part.getContent(), names);
                }
            }
        }

        @Test
        @DisplayName("status=SUCCESS -> письмо уходит БЕЗ вложения")
        void successStatus_SendsEmailWithoutAttachment() throws Exception {
            ImportEvent event = createEvent("SUCCESS", 0, List.of(), null);

            emailService.sendImportReportEmail(event);

            ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
            verify(mailSender).send(captor.capture());
            verify(templateEngine).process(org.mockito.ArgumentMatchers.eq("mail/import-report"), any(Context.class));
            assertThat(attachmentNamesOf(captor.getValue())).isEmpty();
        }

        @Test
        @DisplayName("status=FAILED -> письмо уходит С вложением ftk-import-*.txt, внутри причина сбоя и стектрейс")
        void failedStatus_SendsEmailWithAttachment() throws Exception {
            ImportEvent.ImportError error = new ImportEvent.ImportError(null, "Ошибка подключения к FTP", false);
            ImportEvent event = createEvent("FAILED", 0, List.of(error), "stacktrace-details");

            emailService.sendImportReportEmail(event);

            ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
            verify(mailSender).send(captor.capture());

            // имя файла собирается из startedAt события: 2026-08-13 03:00
            assertThat(attachmentNamesOf(captor.getValue()))
                    .containsExactly("ftk-import-20260813-0300.txt");

            String report = emailService.buildImportReportFile(event);
            assertThat(report).contains("Ошибка подключения к FTP");
            assertThat(report).contains("stacktrace-details");
        }

        @Test
        @DisplayName("status=PARTIAL -> письмо тоже уходит с вложением")
        void partialStatus_SendsEmailWithAttachment() throws Exception {
            ImportEvent.ImportError error = new ImportEvent.ImportError("FTK-1", "слишком длинное значение", false);
            ImportEvent event = createEvent("PARTIAL", 3, List.of(error), null);

            emailService.sendImportReportEmail(event);

            ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
            verify(mailSender).send(captor.capture());
            assertThat(attachmentNamesOf(captor.getValue())).hasSize(1);
        }
    }
}
