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

    public void sendLegalEntityVerificationEmail(String to, String companyName, String confirmUrl) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromEmail);
        message.setTo(to);
        message.setSubject("Подтвердите email организации — РФСнаб");
        message.setText(String.format(
                "Здравствуйте!\n\nВы зарегистрировали организацию \"%s\" на платформе РФСнаб.\n\n" +
                "Для подтверждения email перейдите по ссылке:\n%s\n\n" +
                "Ссылка действительна 24 часа.\n\nС уважением,\nКоманда РФСнаб",
                companyName, confirmUrl));
        mailSender.send(message);
    }

    public void sendLegalEntityEmailConfirmedToManager(String managerEmail, String companyName, String inn) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromEmail);
        message.setTo(managerEmail);
        message.setSubject("Новое юрлицо на верификацию — " + companyName);
        message.setText(String.format(
                "Требуется верификация нового юридического лица:\n\n" +
                "Организация: %s\nИНН: %s\n\n" +
                "Перейдите в панель администратора для проверки документов.",
                companyName, inn));
        mailSender.send(message);
    }

    public void sendLegalEntityVerifiedEmail(String to, String companyName) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromEmail);
        message.setTo(to);
        message.setSubject("Регистрация подтверждена — РФСнаб");
        message.setText(String.format(
                "Поздравляем!\n\nОрганизация \"%s\" прошла верификацию.\n" +
                "Теперь вы можете входить в систему и оформлять заказы.\n\n" +
                "С уважением,\nКоманда РФСнаб",
                companyName));
        mailSender.send(message);
    }

    public void sendLegalEntityRejectedEmail(String to, String companyName, String reason) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromEmail);
        message.setTo(to);
        message.setSubject("Регистрация отклонена — РФСнаб");
        message.setText(String.format(
                "К сожалению, регистрация организации \"%s\" была отклонена.\n\n" +
                "Причина: %s\n\n" +
                "Если у вас есть вопросы, свяжитесь с нашей поддержкой.\n\n" +
                "С уважением,\nКоманда РФСнаб",
                companyName, reason != null ? reason : "не указана"));
        mailSender.send(message);
    }

    public void sendLegalEntityLinkRequestedEmail(String to, String companyName,
                                                   String userName, String confirmUrl) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromEmail);
        message.setTo(to);
        message.setSubject("Запрос на привязку аккаунта — РФСнаб");
        message.setText(String.format(
                "Пользователь %s хочет привязать аккаунт к вашей организации \"%s\".\n\n" +
                "Для подтверждения перейдите по ссылке:\n%s\n\n" +
                "Если вы не ожидали этого запроса — просто проигнорируйте письмо.\n\n" +
                "С уважением,\nКоманда РФСнаб",
                userName, companyName, confirmUrl));
        mailSender.send(message);
    }

    public void sendLegalEntityLinkConfirmedEmail(String toLegal, String toUser,
                                                   String companyName, String userName) {
        SimpleMailMessage msgToLegal = new SimpleMailMessage();
        msgToLegal.setFrom(fromEmail);
        msgToLegal.setTo(toLegal);
        msgToLegal.setSubject("Пользователь привязан к вашей организации — РФСнаб");
        msgToLegal.setText(String.format(
                "Пользователь %s успешно привязан к организации \"%s\".\n\nС уважением,\nКоманда РФСнаб",
                userName, companyName));
        mailSender.send(msgToLegal);

        SimpleMailMessage msgToUser = new SimpleMailMessage();
        msgToUser.setFrom(fromEmail);
        msgToUser.setTo(toUser);
        msgToUser.setSubject("Привязка к организации подтверждена — РФСнаб");
        msgToUser.setText(String.format(
                "Ваш аккаунт успешно привязан к организации \"%s\".\n" +
                "Теперь вы можете переключаться на B2B контекст в личном кабинете.\n\n" +
                "С уважением,\nКоманда РФСнаб", companyName));
        mailSender.send(msgToUser);
    }

    public void sendPaymentApprovedEmail(String to, BigDecimal amount, String paymentMode) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromEmail);
        message.setTo(to);
        message.setSubject("Оплата прошла успешно — РФСнаб");
        message.setText(String.format(
                "Ваш платёж на сумму %.2f ₽ успешно принят (способ: %s).\n\nСпасибо за заказ!\n\nС уважением,\nКоманда РФСнаб",
                amount, paymentMode
        ));
        mailSender.send(message);
    }

    public void sendPaymentFailedEmail(String to) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromEmail);
        message.setTo(to);
        message.setSubject("Ошибка оплаты — РФСнаб");
        message.setText("К сожалению, ваш платёж не прошёл.\n\nПожалуйста, попробуйте снова или обратитесь в поддержку.\n\nС уважением,\nКоманда РФСнаб");
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
