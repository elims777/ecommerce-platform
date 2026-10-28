package ru.rfsnab.notificationservice.service;

import ru.rfsnab.notificationservice.utils.LogMasker;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import ru.rfsnab.notificationservice.models.CarrierSyncEvent;
import ru.rfsnab.notificationservice.models.ImportEvent;
import ru.rfsnab.notificationservice.models.OrderEvent;
import ru.rfsnab.notificationservice.models.ShipmentNotFoundEvent;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private static final String KEY_TERMINAL_NAME = "terminalName";
    private static final String KEY_ORDER_NUMBER = "orderNumber";
    private static final String KEY_CARRIER_NAME = "carrierName";
    private static final String KEY_DELIVERY_METHOD = "deliveryMethod";
    private static final String KEY_TERMINAL_ADDRESS = "terminalAddress";
    private static final String STATUS_FAILED = "FAILED";
    private static final String STATUS_PARTIAL = "PARTIAL";
    private static final String SUBJECT_SUFFIX = " — РФСнаб";
    private static final String KEY_TRACK_URL = "trackUrl";
    private static final String ORDERS_PATH = "/orders";
    private static final String SUBJECT_ORDER_PREFIX = "Заказ ";
    private static final String KEY_COMPANY_NAME = "companyName";
    private static final String KEY_TOTAL_AMOUNT = "totalAmount";
    private static final String KEY_USER_NAME = "userName";
    private static final ZoneId MOSCOW = ZoneId.of("Europe/Moscow");
    private static final DateTimeFormatter MOSCOW_DATE_TIME = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
    private static final DateTimeFormatter IMPORT_REPORT_FILE_DATE_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmm");

    private static final Map<String, String> CARRIER_SYNC_STATUS_LABELS = Map.of(
            "OK", "OK",
            "WARNING", "Предупреждение",
            "ERROR", "Ошибка",
            "NOT_CONFIGURED", "Не настроена");
    private static final Map<String, String> CARRIER_SYNC_TRIGGER_LABELS = Map.of(
            "SCHEDULED", "ночная",
            "STARTUP", "при запуске",
            "MANUAL", "ручная");

    /** Статусы (displayName из order-service), в письмах о которых показывается блок «Доставка / Отправления». */
    private static final Set<String> SHIPMENT_STATUSES = Set.of("Отгружен", "В пути", "Прибыл на терминал", "Доставлен");

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;
    @Value("${app.email.from}")
    private String fromEmail;

    @Value("${app.email.verification-url}")
    private String verificationBaseUrl;

    @Value("${app.email.manager}")
    private String managerEmail;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    public void sendVerificationEmail(String toEmail, String firstName, String verificationToken) {
        log.info("Send verification email to: {}", LogMasker.maskEmail(toEmail));

        String verificationLink = verificationBaseUrl + "?token=" + verificationToken;

        Map<String, Object> model = new HashMap<>();
        model.put("firstName", firstName);
        model.put("verificationLink", verificationLink);

        sendHtml(toEmail, "Подтверждение почтового адреса", "verification", model);
    }

    public void sendPasswordResetEmail(String toEmail, String firstName, String rawToken) {
        log.info("Send password reset email to: {}", LogMasker.maskEmail(toEmail));

        String resetLink = frontendUrl + "/reset-password?token=" + rawToken;

        Map<String, Object> model = new HashMap<>();
        model.put("firstName", firstName);
        model.put("resetLink", resetLink);

        sendHtml(toEmail, "Сброс пароля — РФСнаб", "password-reset", model);
    }

    /**
     * Уведомление о создании заказа
     */
    public void sendOrderCreatedEmail(OrderEvent event) {
        Map<String, Object> model = new HashMap<>();
        model.put(KEY_ORDER_NUMBER, event.orderNumber());
        model.put(KEY_TOTAL_AMOUNT, event.totalAmount());
        model.put("items", event.items());
        model.put("customerName", event.customerName());
        model.put("customerPhone", event.customerPhone());
        model.put("customerEmail", event.customerEmail());
        model.put(KEY_COMPANY_NAME, event.companyName());
        model.put("inn", event.inn());
        model.put("deliveryAddress", event.deliveryAddress());
        model.put("addressLine", formatAddress(event.deliveryAddress()));
        model.put("pickupPoint", event.pickupPoint());
        model.put("pickupLine", formatPickupAddress(event.pickupPoint()));
        model.put(KEY_DELIVERY_METHOD, deliveryMethodLabel(event.deliveryMethod()));
        model.put(KEY_CARRIER_NAME, event.carrierName());
        model.put(KEY_TERMINAL_NAME, event.terminalName());
        model.put(KEY_TERMINAL_ADDRESS, event.terminalAddress());
        model.put("shippingWarehouseCity", event.shippingWarehouseCity());
        model.put("paymentMethod", paymentMethodLabel(event.paymentMethod()));
        model.put("comment", event.comment());
        model.put(KEY_TRACK_URL, frontendUrl + ORDERS_PATH);

        sendHtml(event.customerEmail(), SUBJECT_ORDER_PREFIX + event.orderNumber() + " оформлен — РФСнаб",
                "order-created", model);
    }

    /**
     * Уведомление менеджеру о новом заказе
     */
    public void sendManagerOrderNotification(OrderEvent event) {
        Map<String, Object> model = new HashMap<>();
        model.put(KEY_ORDER_NUMBER, event.orderNumber());
        model.put(KEY_TOTAL_AMOUNT, event.totalAmount());
        model.put("items", event.items());
        model.put("customerName", event.customerName());
        model.put("customerPhone", event.customerPhone());
        model.put("customerEmail", event.customerEmail());
        model.put(KEY_COMPANY_NAME, event.companyName());
        model.put("inn", event.inn());
        model.put("customerType", event.customerType());
        model.put("deliveryAddress", event.deliveryAddress());
        model.put("addressLine", formatAddress(event.deliveryAddress()));
        model.put("pickupPoint", event.pickupPoint());
        model.put("pickupLine", formatPickupAddress(event.pickupPoint()));
        model.put(KEY_DELIVERY_METHOD, deliveryMethodLabel(event.deliveryMethod()));
        model.put(KEY_CARRIER_NAME, event.carrierName());
        model.put(KEY_TERMINAL_NAME, event.terminalName());
        model.put(KEY_TERMINAL_ADDRESS, event.terminalAddress());
        model.put("shippingWarehouseCity", event.shippingWarehouseCity());
        model.put("paymentMethod", paymentMethodLabel(event.paymentMethod()));
        model.put("comment", event.comment());
        model.put("adminUrl", frontendUrl + "/admin/orders");

        sendHtml(managerEmail, "Новый заказ " + event.orderNumber() + SUBJECT_SUFFIX,
                "manager-new-order", model);
    }

    /**
     * Уведомление об успешной оплате
     */
    public void sendOrderPaidEmail(String toEmail, String orderNumber, BigDecimal totalAmount) {
        Map<String, Object> model = new HashMap<>();
        model.put(KEY_ORDER_NUMBER, orderNumber);
        model.put(KEY_TOTAL_AMOUNT, totalAmount);
        model.put(KEY_TRACK_URL, frontendUrl + ORDERS_PATH);

        sendHtml(toEmail, "Оплата заказа " + orderNumber + " подтверждена — РФСнаб", "order-paid", model);
    }

    /**
     * Уведомление об отмене заказа
     */
    public void sendOrderCancelledEmail(String toEmail, String orderNumber) {
        Map<String, Object> model = new HashMap<>();
        model.put(KEY_ORDER_NUMBER, orderNumber);

        sendHtml(toEmail, SUBJECT_ORDER_PREFIX + orderNumber + " отменён — РФСнаб", "order-cancelled", model);
    }

    /**
     * Уведомление о выставлении счёта (INVOICE_SENT)
     */
    public void sendInvoiceSentEmail(String toEmail, String orderNumber) {
        Map<String, Object> model = new HashMap<>();
        model.put(KEY_ORDER_NUMBER, orderNumber);
        model.put(KEY_TRACK_URL, frontendUrl + ORDERS_PATH);

        sendHtml(toEmail, "Счёт по заказу " + orderNumber + SUBJECT_SUFFIX, "invoice-sent", model);
    }

    /**
     * Уведомление об ожидании подтверждения оплаты (AWAITING_CONFIRMATION — B2B постоплата)
     */
    public void sendAwaitingConfirmationEmail(String toEmail, String orderNumber) {
        Map<String, Object> model = new HashMap<>();
        model.put(KEY_ORDER_NUMBER, orderNumber);

        sendHtml(toEmail, SUBJECT_ORDER_PREFIX + orderNumber + " принят в работу — постоплата",
                "awaiting-confirmation", model);
    }

    public void sendLegalEntityVerificationEmail(String to, String companyName, String confirmUrl) {
        Map<String, Object> model = new HashMap<>();
        model.put(KEY_COMPANY_NAME, companyName);
        model.put("confirmUrl", confirmUrl);

        sendHtml(to, "Подтвердите email организации — РФСнаб", "legal-verification", model);
    }

    public void sendLegalEntityEmailConfirmedToManager(String managerEmail, String companyName, String inn) {
        Map<String, Object> model = new HashMap<>();
        model.put(KEY_COMPANY_NAME, companyName);
        model.put("inn", inn);

        sendHtml(managerEmail, "Новое юрлицо на верификацию — " + companyName,
                "legal-email-confirmed-manager", model);
    }

    public void sendLegalEntityVerifiedEmail(String to, String companyName) {
        Map<String, Object> model = new HashMap<>();
        model.put(KEY_COMPANY_NAME, companyName);

        sendHtml(to, "Регистрация подтверждена — РФСнаб", "legal-verified", model);
    }

    public void sendLegalEntityRejectedEmail(String to, String companyName, String reason) {
        Map<String, Object> model = new HashMap<>();
        model.put(KEY_COMPANY_NAME, companyName);
        model.put("reason", reason != null ? reason : "не указана");

        sendHtml(to, "Регистрация отклонена — РФСнаб", "legal-rejected", model);
    }

    public void sendLegalEntityLinkRequestedEmail(String to, String companyName,
                                                   String userName, String confirmUrl) {
        Map<String, Object> model = new HashMap<>();
        model.put(KEY_COMPANY_NAME, companyName);
        model.put(KEY_USER_NAME, userName);
        model.put("confirmUrl", confirmUrl);

        sendHtml(to, "Запрос на привязку аккаунта — РФСнаб", "legal-link-requested", model);
    }

    public void sendLegalEntityLinkConfirmedEmail(String toLegal, String toUser,
                                                   String companyName, String userName) {
        Map<String, Object> modelLegal = new HashMap<>();
        modelLegal.put(KEY_COMPANY_NAME, companyName);
        modelLegal.put(KEY_USER_NAME, userName);
        sendHtml(toLegal, "Пользователь привязан к вашей организации — РФСнаб",
                "legal-link-confirmed-owner", modelLegal);

        Map<String, Object> modelUser = new HashMap<>();
        modelUser.put(KEY_COMPANY_NAME, companyName);
        modelUser.put(KEY_USER_NAME, userName);
        sendHtml(toUser, "Привязка к организации подтверждена — РФСнаб",
                "legal-link-confirmed-user", modelUser);
    }

    public void sendLegalEntityUnlinkedEmail(String toLegal, String toUser,
                                              String companyName, String userName) {
        Map<String, Object> modelLegal = new HashMap<>();
        modelLegal.put(KEY_COMPANY_NAME, companyName);
        modelLegal.put(KEY_USER_NAME, userName);
        sendHtml(toLegal, "Пользователь отвязан от вашей организации — РФСнаб",
                "legal-unlinked-owner", modelLegal);

        Map<String, Object> modelUser = new HashMap<>();
        modelUser.put(KEY_COMPANY_NAME, companyName);
        modelUser.put(KEY_USER_NAME, userName);
        sendHtml(toUser, "Организация отвязана — РФСнаб", "legal-unlinked-user", modelUser);
    }

    public void sendInactivityEmail(String to, String firstname, String catalogUrl, String unsubscribeUrl) {
        Map<String, Object> model = new HashMap<>();
        model.put("firstname", firstname);
        model.put("catalogUrl", catalogUrl);
        model.put("unsubscribeUrl", unsubscribeUrl);

        sendHtml(to, "Мы соскучились, " + firstname + "! 👋 Посмотрите наши актуальные предложения",
                "inactivity", model);
    }

    public void sendPaymentApprovedEmail(String to, BigDecimal amount, String paymentMode) {
        Map<String, Object> model = new HashMap<>();
        model.put("amount", amount);
        model.put("paymentMode", paymentMode);

        sendHtml(to, "Оплата прошла успешно — РФСнаб", "payment-approved", model);
    }

    public void sendPaymentFailedEmail(String to) {
        sendHtml(to, "Ошибка оплаты — РФСнаб", "payment-failed", Map.of());
    }

    /**
     * Уведомление о добавлении документа к заказу (счёт, УПД, КП, сертификат)
     */
    public void sendOrderDocumentAddedEmail(String toEmail, String orderNumber, String documentType) {
        Map<String, Object> model = new HashMap<>();
        model.put(KEY_ORDER_NUMBER, orderNumber);
        model.put("documentType", documentType);
        model.put(KEY_TRACK_URL, frontendUrl + ORDERS_PATH);

        sendHtml(toEmail, documentType + " к заказу " + orderNumber + SUBJECT_SUFFIX,
                "order-document-added", model);
    }

    /**
     * Уведомление о смене статуса заказа
     */
    public void sendOrderStatusChangedEmail(OrderEvent event) {
        List<OrderEvent.ShipmentLine> shipments = event.shipments() == null ? List.of() : event.shipments();
        boolean showDelivery = SHIPMENT_STATUSES.contains(event.status())
                && (event.carrierName() != null || event.terminalName() != null || !shipments.isEmpty());

        Map<String, Object> model = new HashMap<>();
        model.put(KEY_ORDER_NUMBER, event.orderNumber());
        model.put("statusLabel", event.status());
        model.put("showDelivery", showDelivery);
        model.put(KEY_DELIVERY_METHOD, deliveryMethodLabel(event.deliveryMethod()));
        model.put(KEY_CARRIER_NAME, event.carrierName());
        model.put(KEY_TERMINAL_NAME, event.terminalName());
        model.put(KEY_TERMINAL_ADDRESS, event.terminalAddress());
        model.put("shipments", shipments);

        sendHtml(event.customerEmail(), "Статус заказа " + event.orderNumber() + " обновлён — РФСнаб",
                "order-status-changed", model);
    }

    /**
     * Уведомление менеджеру: накладная ТК не найдена в системе перевозчика.
     */
    public void sendShipmentNotFoundEmail(ShipmentNotFoundEvent event) {
        Map<String, Object> model = new HashMap<>();
        model.put(KEY_ORDER_NUMBER, event.orderNumber());
        model.put(KEY_CARRIER_NAME, event.carrierName());
        model.put("waybillNumber", event.waybillNumber());
        model.put("notFoundSince", formatMoscow(event.notFoundSince()));

        sendHtml(managerEmail, "Накладная " + event.waybillNumber() + " (" + event.carrierName()
                + ") по заказу " + event.orderNumber() + " не найдена", "shipment-not-found", model);
    }

    /** ISO-момент -> «dd.MM.yyyy HH:mm» по Москве; null и нераспознанная строка возвращаются как есть. */
    private static String formatMoscow(String isoInstant) {
        if (isoInstant == null) {
            return null;
        }
        try {
            return MOSCOW_DATE_TIME.format(Instant.parse(isoInstant).atZone(MOSCOW));
        } catch (DateTimeParseException e) {
            return isoInstant;
        }
    }

    /**
     * Отчёт о ночном импорте каталога ФТК менеджеру — всегда, независимо от статуса.
     */
    public void sendImportReportEmail(ImportEvent event) {
        Map<String, Object> model = new HashMap<>();
        model.put("status", importStatusLabel(event.status()));
        model.put("totalReceived", event.totalReceived());
        model.put("created", event.created());
        model.put("updated", event.updated());
        model.put("unchanged", event.unchanged());
        model.put("failed", event.failed());
        model.put("imagesProcessed", event.imagesProcessed());
        model.put("imagesFailed", event.imagesFailed());
        model.put("durationMinutes", event.durationMs() / 60000.0);
        model.put("startedAt", event.startedAt());
        model.put("cascadeCount", event.cascadeCount());
        model.put("rootErrors", event.errors() == null ? List.of() :
                event.errors().stream().filter(err -> !err.cascade()).toList());
        model.put("rootCause", event.rootCause());

        String subject = importSubject(event);
        if (STATUS_FAILED.equals(event.status()) || STATUS_PARTIAL.equals(event.status())) {
            LocalDateTime startedAt = event.startedAt() != null ? event.startedAt() : LocalDateTime.now();
            String attachmentName = "ftk-import-" + startedAt.format(IMPORT_REPORT_FILE_DATE_FORMAT) + ".txt";
            byte[] attachmentContent = buildImportReportFile(event).getBytes(StandardCharsets.UTF_8);
            sendHtml(managerEmail, subject, "import-report", model, attachmentName, attachmentContent);
        } else {
            sendHtml(managerEmail, subject, "import-report", model);
        }
    }

    /**
     * Текстовый файл с деталями импорта — вложение к письму при FAILED/PARTIAL,
     * чтобы причина сбоя и стектрейс не терялись, если тело письма урезано.
     */
    String buildImportReportFile(ImportEvent event) {
        List<ImportEvent.ImportError> rootErrors = event.errors() == null ? List.of() :
                event.errors().stream().filter(err -> !err.cascade()).toList();

        StringBuilder sb = new StringBuilder();
        sb.append("Импорт каталога ФТК\n");
        sb.append("Статус: ").append(event.status()).append('\n');
        sb.append("Начат: ").append(event.startedAt()).append('\n');
        sb.append("Длительность: ").append(String.format("%.1f", event.durationMs() / 60000.0)).append(" мин\n");
        sb.append('\n');
        sb.append("Получено: ").append(event.totalReceived())
                .append(" | Создано: ").append(event.created())
                .append(" | Обновлено: ").append(event.updated())
                .append(" | Без изменений: ").append(event.unchanged())
                .append(" | Ошибок: ").append(event.failed()).append('\n');
        sb.append("Фото: обработано ").append(event.imagesProcessed())
                .append(", с ошибками ").append(event.imagesFailed()).append('\n');
        sb.append("Каскадных ошибок: ").append(event.cascadeCount()).append('\n');
        sb.append('\n');
        sb.append("=== ПРИЧИНА СБОЯ ===\n");
        String reason = rootErrors.isEmpty() ? null : rootErrors.get(0).message();
        if (reason == null || reason.isBlank()) {
            reason = event.errorStacktrace() != null && !event.errorStacktrace().isBlank()
                    ? "сообщение отсутствует, см. стектрейс ниже"
                    : "не указана";
        }
        sb.append(reason).append('\n');
        if (event.rootCause() != null && !event.rootCause().isBlank()) {
            sb.append("Первопричина: ").append(event.rootCause()).append('\n');
        }

        if (event.errorStacktrace() != null && !event.errorStacktrace().isBlank()) {
            sb.append('\n');
            sb.append("=== СТЕКТРЕЙС ===\n");
            sb.append(event.errorStacktrace()).append('\n');
        }

        if (!rootErrors.isEmpty()) {
            sb.append('\n');
            sb.append("=== ОШИБКИ ТОВАРОВ (первые 50) ===\n");
            rootErrors.forEach(err -> {
                // message может быть null (исключение без сообщения) — "null" в отчёте недопустим
                String message = err.message() != null && !err.message().isBlank()
                        ? err.message()
                        : "сообщение отсутствует, см. стектрейс выше";
                if (err.externalId() != null) {
                    sb.append(err.externalId()).append(": ").append(message).append('\n');
                } else {
                    sb.append(message).append('\n');
                }
            });
        }

        return sb.toString();
    }

    String importSubject(ImportEvent event) {
        if (STATUS_FAILED.equals(event.status())) {
            return "Импорт каталога ФТК — ОШИБКА";
        }
        if (event.failed() > 0) {
            return "Импорт каталога ФТК — " + event.failed() + " ошибок";
        }
        // PARTIAL бывает и при failed=0 — когда упали только картинки (см. saveFtkLog)
        if (STATUS_PARTIAL.equals(event.status())) {
            return "Импорт каталога ФТК — частично, ошибок фото: " + event.imagesFailed();
        }
        return "Импорт каталога ФТК — успешно";
    }

    /**
     * Отчёт о синхронизации терминалов ТК менеджеру — после каждого запуска.
     */
    public void sendCarrierSyncReportEmail(CarrierSyncEvent event) {
        Map<String, Object> model = new HashMap<>();
        model.put("triggerLabel", CARRIER_SYNC_TRIGGER_LABELS.getOrDefault(event.trigger(), String.valueOf(event.trigger())));
        model.put("reports", event.reports() == null ? List.of() : event.reports());
        model.put("statusLabels", CARRIER_SYNC_STATUS_LABELS);
        sendHtml(managerEmail, carrierSyncSubject(event), "carrier-sync-report", model);
    }

    /**
     * Тема письма по худшему статусу среди отчётов: ERROR, затем WARNING/NOT_CONFIGURED, иначе OK.
     */
    String carrierSyncSubject(CarrierSyncEvent event) {
        List<CarrierSyncEvent.CarrierSyncReport> reports = event.reports() == null ? List.of() : event.reports();
        String result;
        if (reports.stream().anyMatch(r -> "ERROR".equals(r.status()))) {
            result = "ошибки";
        } else if (reports.stream().anyMatch(r -> "WARNING".equals(r.status()) || "NOT_CONFIGURED".equals(r.status()))) {
            result = "есть предупреждения";
        } else {
            result = "OK";
        }
        return "Синхронизация терминалов ТК: " + result;
    }

    private String importStatusLabel(String status) {
        if (status == null) {
            return "—";
        }
        return switch (status) {
            case "SUCCESS" -> "Успешно";
            case STATUS_PARTIAL -> "Частично с ошибками";
            case STATUS_FAILED -> "Провален";
            default -> status;
        };
    }

    /**
     * Собирает адрес в одну строку из непустых полей — чтобы в письме не появлялось "null".
     */
    private String formatAddress(OrderEvent.DeliveryAddressDto a) {
        if (a == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        appendPart(sb, a.postalCode());
        appendPart(sb, a.city());
        appendPart(sb, a.street());
        appendPart(sb, a.building());
        if (a.apartment() != null && !a.apartment().isBlank()) {
            appendPart(sb, "кв. " + a.apartment());
        }
        return sb.isEmpty() ? null : sb.toString();
    }

    /**
     * Собирает адрес пункта самовывоза в одну строку из непустых полей.
     */
    private String formatPickupAddress(OrderEvent.PickupPointDto p) {
        if (p == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        appendPart(sb, p.postalCode());
        appendPart(sb, p.city());
        appendPart(sb, p.street());
        appendPart(sb, p.building());
        return sb.isEmpty() ? null : sb.toString();
    }

    private void appendPart(StringBuilder sb, String part) {
        if (part != null && !part.isBlank()) {
            if (!sb.isEmpty()) {
                sb.append(", ");
            }
            sb.append(part);
        }
    }

    private String deliveryMethodLabel(String deliveryMethod) {
        if (deliveryMethod == null) {
            return "—";
        }
        return switch (deliveryMethod) {
            case "PICKUP" -> "Самовывоз";
            case "SUPPLIER_DELIVERY" -> "Доставка поставщиком";
            case "CARRIER_ADDRESS" -> "Доставка ТК до двери";
            case "CARRIER_TERMINAL" -> "Доставка до терминала (ТК)";
            default -> deliveryMethod;
        };
    }

    private String paymentMethodLabel(String paymentMethod) {
        if (paymentMethod == null) {
            return "—";
        }
        return switch (paymentMethod) {
            case "CARD" -> "Банковская карта";
            case "SBP" -> "Система быстрых платежей";
            case "CASH_ON_DELIVERY" -> "Оплата при получении";
            case "INVOICE" -> "Выставить счёт";
            default -> paymentMethod;
        };
    }

    /**
     * Отправка HTML-письма по Thymeleaf-шаблону с inline-логотипом (CID).
     */
    private void sendHtml(String to, String subject, String template, Map<String, Object> model) {
        sendHtml(to, subject, template, model, null, null);
    }

    /**
     * Отправка HTML-письма с опциональным текстовым вложением (например, отчёт об ошибке импорта).
     */
    private void sendHtml(String to, String subject, String template, Map<String, Object> model,
                           String attachmentName, byte[] attachmentContent) {
        MimeMessage message = mailSender.createMimeMessage();
        try {
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            Context context = new Context();
            context.setVariables(model);
            String html = templateEngine.process("mail/" + template, context);

            helper.setFrom(fromEmail);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(html, true);
            helper.addInline("logo", new ClassPathResource("static/mail/logo-light.png"), "image/png");

            if (attachmentName != null && attachmentContent != null) {
                helper.addAttachment(attachmentName, new ByteArrayResource(attachmentContent), "text/plain; charset=UTF-8");
            }

            mailSender.send(message);
            log.info("HTML email '{}' sent successfully to: {}", template, LogMasker.maskEmail(to));
        } catch (MessagingException e) {
            log.error("Failed to build HTML email '{}' for: {}", template, LogMasker.maskEmail(to), e);
        } catch (Exception e) {
            log.error("Failed to send HTML email '{}' to: {}", template, LogMasker.maskEmail(to), e);
        }
    }
}
