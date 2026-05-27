package ru.rfsnab.notificationservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {
    private final JavaMailSender mailSender;
    @Value("${app.email.from}")
    private String fromEmail;

    @Value("${app.email.verification-url}")
    private String verificationBaseUrl;

    public void sendVerificationEmail(String toEmail, String firstName, String verificationToken){
        log.info("Send verification email to: {}", toEmail);

        String verificationLink = verificationBaseUrl + "?token=" + verificationToken;

        String emailText = String.format("""
                Добрый день, %s !
                
                Спасибо за регистрацию!
                Пожалуйста, подтвердите ваш email, перейдя по ссылке ниже: 
                
                %s
                
                Эта ссылка действует 1 час.
                Если это письмо пришло по ошибке, просто проигнорируйте его.
                
                С наилучшими пожеаниями,
                Команда rfsnab.ru!
                
                """, firstName, verificationLink);

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromEmail);
        message.setTo(toEmail);
        message.setSubject("Подтверждение почтового адреса");
        message.setText(emailText);

        try{
            mailSender.send(message);
            log.info("Email send successfully to: {}", toEmail);
        } catch(Exception e){
            log.error("Failed to send email to: {}", toEmail, e);
            throw new RuntimeException("Failed to send email", e);
        }
    }

    /**
     * Уведомление о создании заказа
     */
    public void sendOrderCreatedEmail(String toEmail, String orderNumber, BigDecimal totalAmount) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromEmail);
        message.setTo(toEmail);
        message.setSubject("Заказ " + orderNumber + " оформлен — РФСнаб");
        message.setText(String.format(
                """
                Ваш заказ %s успешно оформлен и передан менеджеру!
                
                Сумма заказа: %s ₽
                
                Менеджер свяжется с вами для подтверждения.
                После подтверждения заказ перейдёт в статус ожидания оплаты.
                
                Отслеживайте статус в личном кабинете.
                
                С уважением,
                Команда РФСнаб
                """,
                orderNumber, totalAmount.toPlainString()
        ));
        mailSender.send(message);
    }

    /**
     * Уведомление об успешной оплате
     */
    public void sendOrderPaidEmail(String toEmail, String orderNumber, BigDecimal totalAmount) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromEmail);
        message.setTo(toEmail);
        message.setSubject("Оплата заказа " + orderNumber + " подтверждена — РФСнаб");
        message.setText(String.format(
                """
                Оплата заказа %s на сумму %s ₽ подтверждена!
                
                Заказ передан в работу. Мы уведомим вас об отправке.
                
                Отслеживайте статус в личном кабинете.
                
                С уважением,
                Команда РФСнаб
                """,
                orderNumber, totalAmount.toPlainString()
        ));
        mailSender.send(message);
    }

    /**
     * Уведомление об отмене заказа
     */
    public void sendOrderCancelledEmail(String toEmail, String orderNumber) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromEmail);
        message.setTo(toEmail);
        message.setSubject("Заказ " + orderNumber + " отменён — РФСнаб");
        message.setText(String.format(
                """
                Заказ %s был отменён.
                
                Если оплата была произведена, средства будут возвращены
                в течение 3-5 рабочих дней.
                
                Если у вас есть вопросы — ответьте на это письмо.
                
                С уважением,
                Команда РФСнаб
                """,
                orderNumber
        ));
        mailSender.send(message);
    }

    /**
     * Уведомление о выставлении счёта (INVOICE_SENT)
     */
    public void sendInvoiceSentEmail(String toEmail, String orderNumber) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromEmail);
        message.setTo(toEmail);
        message.setSubject("Счёт по заказу " + orderNumber + " — РФСнаб");
        message.setText(String.format(
                """
                Добрый день!

                По вашему заказу %s выставлен счёт на оплату.
                Счёт направлен на ваш email отдельным письмом.

                После оплаты счёта менеджер подтвердит получение и заказ будет передан в отгрузку.

                Отслеживайте статус в личном кабинете.

                С уважением,
                Команда РФСнаб
                """,
                orderNumber
        ));
        mailSender.send(message);
    }

    /**
     * Уведомление об ожидании подтверждения оплаты (AWAITING_CONFIRMATION — B2B постоплата)
     */
    public void sendAwaitingConfirmationEmail(String toEmail, String orderNumber) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromEmail);
        message.setTo(toEmail);
        message.setSubject("Заказ " + orderNumber + " отгружен — ожидаем подтверждения оплаты");
        message.setText(String.format(
                """
                Добрый день!

                Ваш заказ %s передан в доставку.

                Согласно условиям договора, оплата производится после получения товара.
                Просим подтвердить получение и произвести оплату в установленный срок.

                Если у вас есть вопросы — ответьте на это письмо или свяжитесь с менеджером.

                С уважением,
                Команда РФСнаб
                """,
                orderNumber
        ));
        mailSender.send(message);
    }

    /**
     * Уведомление о смене статуса заказа
     */
    public void sendOrderStatusChangedEmail(String toEmail, String orderNumber, String newStatus) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromEmail);
        message.setTo(toEmail);
        message.setSubject("Статус заказа " + orderNumber + " обновлён — РФСнаб");
        message.setText(String.format(
                """
                Статус вашего заказа %s изменён.
                
                Новый статус: %s
                
                Отслеживайте заказ в личном кабинете.
                
                С уважением,
                Команда РФСнаб
                """,
                orderNumber, newStatus
        ));
        mailSender.send(message);
    }
}
