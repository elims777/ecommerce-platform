package ru.rfsnab.notificationservice.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import ru.rfsnab.notificationservice.models.LegalEntityEvent;
import ru.rfsnab.notificationservice.service.EmailService;

@Slf4j
@Component
public class LegalEntityHandler implements NotificationHandler {

    private final EmailService emailService;
    private final ObjectMapper objectMapper;
    private final String confirmBaseUrl;
    private final String linkConfirmBaseUrl;
    private final String managerEmail;

    public LegalEntityHandler(
            EmailService emailService,
            ObjectMapper objectMapper,
            @Value("${app.email.legal-entity-confirm-url}") String confirmBaseUrl,
            @Value("${app.email.legal-entity-link-confirm-url}") String linkConfirmBaseUrl,
            @Value("${app.email.manager}") String managerEmail) {
        this.emailService = emailService;
        this.objectMapper = objectMapper;
        this.confirmBaseUrl = confirmBaseUrl;
        this.linkConfirmBaseUrl = linkConfirmBaseUrl;
        this.managerEmail = managerEmail;
    }

    @Override
    public boolean supports(String topic, String eventType) {
        return "legal-entity-events".equals(topic);
    }

    @Override
    public void handle(String eventJson) {
        try {
            LegalEntityEvent event = objectMapper.readValue(eventJson, LegalEntityEvent.class);
            switch (event.eventType()) {
                case "LEGAL_ENTITY_REGISTERED" ->
                        emailService.sendLegalEntityVerificationEmail(
                                event.legalEntityEmail(), event.companyName(),
                                confirmBaseUrl + "?token=" + event.token());
                case "LEGAL_ENTITY_EMAIL_CONFIRMED" ->
                        emailService.sendLegalEntityEmailConfirmedToManager(
                                managerEmail, event.companyName(), event.inn());
                case "LEGAL_ENTITY_VERIFIED" ->
                        emailService.sendLegalEntityVerifiedEmail(
                                event.legalEntityEmail(), event.companyName());
                case "LEGAL_ENTITY_REJECTED" ->
                        emailService.sendLegalEntityRejectedEmail(
                                event.legalEntityEmail(), event.companyName(), event.rejectionReason());
                case "LEGAL_ENTITY_LINK_REQUESTED" ->
                        emailService.sendLegalEntityLinkRequestedEmail(
                                event.legalEntityEmail(), event.companyName(),
                                event.rejectionReason(),
                                linkConfirmBaseUrl + "?token=" + event.token());
                case "LEGAL_ENTITY_LINK_CONFIRMED" ->
                        emailService.sendLegalEntityLinkConfirmedEmail(
                                event.legalEntityEmail(), event.targetEmail(),
                                event.companyName(), event.rejectionReason());
                default -> log.warn("Неизвестный тип события юрлица: {}", event.eventType());
            }
        } catch (Exception e) {
            log.error("Ошибка обработки события юрлица: {}", eventJson, e);
        }
    }
}
