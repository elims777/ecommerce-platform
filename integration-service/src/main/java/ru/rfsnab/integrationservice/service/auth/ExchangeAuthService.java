package ru.rfsnab.integrationservice.service.auth;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.rfsnab.integrationservice.config.IntegrationProperties;
import ru.rfsnab.integrationservice.model.ExchangeSession;
import ru.rfsnab.integrationservice.model.ExchangeSession.ExchangeType;
import ru.rfsnab.integrationservice.repository.ExchangeSessionRepository;

import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

/**
 * Сервис авторизации для протокола CommerceML.
 *
 * Протокол: 1С отправляет Basic auth → мы проверяем логин/пароль →
 * создаём сессию → возвращаем cookie. Все последующие запросы 1С
 * содержат эту cookie для идентификации сессии.
 */
@Service
@RequiredArgsConstructor
public class ExchangeAuthService {

    private static final String COOKIE_NAME = "exchange_session";
    private static final int SESSION_TTL_HOURS = 2;

    private final ExchangeSessionRepository sessionRepository;
    private final IntegrationProperties properties;

    /**
     * Проверка Basic auth и создание сессии.
     *
     * @param authHeader заголовок Authorization из запроса 1С
     * @param type       тип обмена (catalog/sale)
     * @return sessionId для cookie, или empty если авторизация не прошла
     */
    @Transactional
    public Optional<String> authenticate(String authHeader, ExchangeType type) {
        if (authHeader == null || !authHeader.startsWith("Basic ")) {
            return Optional.empty();
        }

        String decoded = new String(Base64.getDecoder().decode(authHeader.substring(6)));
        String[] credentials = decoded.split(":", 2);

        if (credentials.length != 2) {
            return Optional.empty();
        }

        String username = credentials[0];
        String password = credentials[1];

        if (!properties.getCommerceml().getUsername().equals(username)
                || !properties.getCommerceml().getPassword().equals(password)) {
            return Optional.empty();
        }

        // Создаём сессию
        ExchangeSession session = ExchangeSession.builder()
                .sessionId(UUID.randomUUID().toString().replace("-", ""))
                .exchangeType(type)
                .expiresAt(LocalDateTime.now().plusHours(SESSION_TTL_HOURS))
                .build();

        sessionRepository.save(session);

        return Optional.of(session.getSessionId());
    }

    /**
     * Валидация сессии по cookie из запроса 1С.
     *
     * @param cookieValue значение cookie exchange_session
     * @return сессия, или empty если невалидная/просроченная
     */
    @Transactional(readOnly = true)
    public Optional<ExchangeSession> validateSession(String cookieValue) {
        if (cookieValue == null || cookieValue.isBlank()) {
            return Optional.empty();
        }

        return sessionRepository.findBySessionId(cookieValue)
                .filter(ExchangeSession::isValid);
    }

    /**
     * Завершение сессии (после mode=success или по таймауту).
     */
    @Transactional
    public void completeSession(String sessionId) {
        sessionRepository.findBySessionId(sessionId)
                .ifPresent(session -> session.setStatus(ExchangeSession.SessionStatus.COMPLETED));
    }

    public String getCookieName() {
        return COOKIE_NAME;
    }
}