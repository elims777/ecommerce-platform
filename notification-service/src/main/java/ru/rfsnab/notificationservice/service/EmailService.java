package ru.rfsnab.notificationservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

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
}
