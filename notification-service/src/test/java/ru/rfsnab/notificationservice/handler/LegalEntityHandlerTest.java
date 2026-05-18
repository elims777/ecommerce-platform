package ru.rfsnab.notificationservice.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.rfsnab.notificationservice.models.LegalEntityEvent;
import ru.rfsnab.notificationservice.service.EmailService;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class LegalEntityHandlerTest {

    @Mock EmailService emailService;
    LegalEntityHandler handler;
    ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        handler = new LegalEntityHandler(emailService, mapper,
                "http://confirm.test", "http://link.test", "manager@rfsnab.ru");
    }

    @Test
    void handles_LEGAL_ENTITY_REGISTERED() throws Exception {
        String json = mapper.writeValueAsString(new LegalEntityEvent(
                "LEGAL_ENTITY_REGISTERED", 1L, "1234567890", "ООО Тест",
                "legal@test.ru", "legal@test.ru", null, LocalDateTime.now(), "tok-123"));

        handler.handle(json);

        verify(emailService).sendLegalEntityVerificationEmail(
                eq("legal@test.ru"), eq("ООО Тест"), contains("http://confirm.test"));
    }

    @Test
    void handles_LEGAL_ENTITY_VERIFIED() throws Exception {
        String json = mapper.writeValueAsString(new LegalEntityEvent(
                "LEGAL_ENTITY_VERIFIED", 1L, "1234567890", "ООО Тест",
                "legal@test.ru", "legal@test.ru", null, LocalDateTime.now(), null));

        handler.handle(json);

        verify(emailService).sendLegalEntityVerifiedEmail("legal@test.ru", "ООО Тест");
    }

    @Test
    void handles_LEGAL_ENTITY_REJECTED() throws Exception {
        String json = mapper.writeValueAsString(new LegalEntityEvent(
                "LEGAL_ENTITY_REJECTED", 1L, "1234567890", "ООО Тест",
                "legal@test.ru", "legal@test.ru", "Неверные документы", LocalDateTime.now(), null));

        handler.handle(json);

        verify(emailService).sendLegalEntityRejectedEmail("legal@test.ru", "ООО Тест", "Неверные документы");
    }

    @Test
    void supports_legalEntityEventsTopic() {
        assertThat(handler.supports("legal-entity-events", "LEGAL_ENTITY_REGISTERED")).isTrue();
        assertThat(handler.supports("order-events", "LEGAL_ENTITY_REGISTERED")).isFalse();
        assertThat(handler.supports("legal-entity-events", "UNKNOWN_EVENT")).isTrue();
    }
}
