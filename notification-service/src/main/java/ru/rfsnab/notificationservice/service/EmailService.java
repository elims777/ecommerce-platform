package ru.rfsnab.notificationservice.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import ru.rfsnab.notificationservice.models.OrderEvent;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {
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
        log.info("Send verification email to: {}", toEmail);

        String verificationLink = verificationBaseUrl + "?token=" + verificationToken;

        Map<String, Object> model = new HashMap<>();
        model.put("firstName", firstName);
        model.put("verificationLink", verificationLink);

        sendHtml(toEmail, "Подтверждение почтового адреса", "verification", model);
    }

    public void sendPasswordResetEmail(String toEmail, String firstName, String rawToken) {
        log.info("Send password reset email to: {}", toEmail);

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
        model.put("orderNumber", event.orderNumber());
        model.put("totalAmount", event.totalAmount());
        model.put("items", event.items());
        model.put("deliveryAddress", event.deliveryAddress());
        model.put("addressLine", formatAddress(event.deliveryAddress()));
        model.put("pickupPoint", event.pickupPoint());
        model.put("pickupLine", formatPickupAddress(event.pickupPoint()));
        model.put("deliveryMethod", deliveryMethodLabel(event.deliveryMethod()));
        model.put("paymentMethod", paymentMethodLabel(event.paymentMethod()));
        model.put("comment", event.comment());
        model.put("trackUrl", frontendUrl + "/orders");

        sendHtml(event.customerEmail(), "Заказ " + event.orderNumber() + " оформлен — РФСнаб",
                "order-created", model);
    }

    /**
     * Уведомление менеджеру о новом заказе
     */
    public void sendManagerOrderNotification(OrderEvent event) {
        Map<String, Object> model = new HashMap<>();
        model.put("orderNumber", event.orderNumber());
        model.put("totalAmount", event.totalAmount());
        model.put("items", event.items());
        model.put("customerName", event.customerName());
        model.put("customerPhone", event.customerPhone());
        model.put("customerEmail", event.customerEmail());
        model.put("companyName", event.companyName());
        model.put("inn", event.inn());
        model.put("customerType", event.customerType());
        model.put("deliveryAddress", event.deliveryAddress());
        model.put("addressLine", formatAddress(event.deliveryAddress()));
        model.put("pickupPoint", event.pickupPoint());
        model.put("pickupLine", formatPickupAddress(event.pickupPoint()));
        model.put("deliveryMethod", deliveryMethodLabel(event.deliveryMethod()));
        model.put("paymentMethod", paymentMethodLabel(event.paymentMethod()));
        model.put("comment", event.comment());
        model.put("adminUrl", frontendUrl + "/admin/orders");

        sendHtml(managerEmail, "Новый заказ " + event.orderNumber() + " — РФСнаб",
                "manager-new-order", model);
    }

    /**
     * Уведомление об успешной оплате
     */
    public void sendOrderPaidEmail(String toEmail, String orderNumber, BigDecimal totalAmount) {
        Map<String, Object> model = new HashMap<>();
        model.put("orderNumber", orderNumber);
        model.put("totalAmount", totalAmount);
        model.put("trackUrl", frontendUrl + "/orders");

        sendHtml(toEmail, "Оплата заказа " + orderNumber + " подтверждена — РФСнаб", "order-paid", model);
    }

    /**
     * Уведомление об отмене заказа
     */
    public void sendOrderCancelledEmail(String toEmail, String orderNumber) {
        Map<String, Object> model = new HashMap<>();
        model.put("orderNumber", orderNumber);

        sendHtml(toEmail, "Заказ " + orderNumber + " отменён — РФСнаб", "order-cancelled", model);
    }

    /**
     * Уведомление о выставлении счёта (INVOICE_SENT)
     */
    public void sendInvoiceSentEmail(String toEmail, String orderNumber) {
        Map<String, Object> model = new HashMap<>();
        model.put("orderNumber", orderNumber);
        model.put("trackUrl", frontendUrl + "/orders");

        sendHtml(toEmail, "Счёт по заказу " + orderNumber + " — РФСнаб", "invoice-sent", model);
    }

    /**
     * Уведомление об ожидании подтверждения оплаты (AWAITING_CONFIRMATION — B2B постоплата)
     */
    public void sendAwaitingConfirmationEmail(String toEmail, String orderNumber) {
        Map<String, Object> model = new HashMap<>();
        model.put("orderNumber", orderNumber);

        sendHtml(toEmail, "Заказ " + orderNumber + " принят в работу — постоплата",
                "awaiting-confirmation", model);
    }

    public void sendLegalEntityVerificationEmail(String to, String companyName, String confirmUrl) {
        Map<String, Object> model = new HashMap<>();
        model.put("companyName", companyName);
        model.put("confirmUrl", confirmUrl);

        sendHtml(to, "Подтвердите email организации — РФСнаб", "legal-verification", model);
    }

    public void sendLegalEntityEmailConfirmedToManager(String managerEmail, String companyName, String inn) {
        Map<String, Object> model = new HashMap<>();
        model.put("companyName", companyName);
        model.put("inn", inn);

        sendHtml(managerEmail, "Новое юрлицо на верификацию — " + companyName,
                "legal-email-confirmed-manager", model);
    }

    public void sendLegalEntityVerifiedEmail(String to, String companyName) {
        Map<String, Object> model = new HashMap<>();
        model.put("companyName", companyName);

        sendHtml(to, "Регистрация подтверждена — РФСнаб", "legal-verified", model);
    }

    public void sendLegalEntityRejectedEmail(String to, String companyName, String reason) {
        Map<String, Object> model = new HashMap<>();
        model.put("companyName", companyName);
        model.put("reason", reason != null ? reason : "не указана");

        sendHtml(to, "Регистрация отклонена — РФСнаб", "legal-rejected", model);
    }

    public void sendLegalEntityLinkRequestedEmail(String to, String companyName,
                                                   String userName, String confirmUrl) {
        Map<String, Object> model = new HashMap<>();
        model.put("companyName", companyName);
        model.put("userName", userName);
        model.put("confirmUrl", confirmUrl);

        sendHtml(to, "Запрос на привязку аккаунта — РФСнаб", "legal-link-requested", model);
    }

    public void sendLegalEntityLinkConfirmedEmail(String toLegal, String toUser,
                                                   String companyName, String userName) {
        Map<String, Object> modelLegal = new HashMap<>();
        modelLegal.put("companyName", companyName);
        modelLegal.put("userName", userName);
        sendHtml(toLegal, "Пользователь привязан к вашей организации — РФСнаб",
                "legal-link-confirmed-owner", modelLegal);

        Map<String, Object> modelUser = new HashMap<>();
        modelUser.put("companyName", companyName);
        modelUser.put("userName", userName);
        sendHtml(toUser, "Привязка к организации подтверждена — РФСнаб",
                "legal-link-confirmed-user", modelUser);
    }

    public void sendLegalEntityUnlinkedEmail(String toLegal, String toUser,
                                              String companyName, String userName) {
        Map<String, Object> modelLegal = new HashMap<>();
        modelLegal.put("companyName", companyName);
        modelLegal.put("userName", userName);
        sendHtml(toLegal, "Пользователь отвязан от вашей организации — РФСнаб",
                "legal-unlinked-owner", modelLegal);

        Map<String, Object> modelUser = new HashMap<>();
        modelUser.put("companyName", companyName);
        modelUser.put("userName", userName);
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
     * Уведомление о смене статуса заказа
     */
    public void sendOrderStatusChangedEmail(String toEmail, String orderNumber, String newStatus) {
        Map<String, Object> model = new HashMap<>();
        model.put("orderNumber", orderNumber);
        model.put("statusLabel", newStatus);

        sendHtml(toEmail, "Статус заказа " + orderNumber + " обновлён — РФСнаб",
                "order-status-changed", model);
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

            mailSender.send(message);
            log.info("HTML email '{}' sent successfully to: {}", template, to);
        } catch (MessagingException e) {
            log.error("Failed to build HTML email '{}' for: {}", template, to, e);
        } catch (Exception e) {
            log.error("Failed to send HTML email '{}' to: {}", template, to, e);
        }
    }
}
