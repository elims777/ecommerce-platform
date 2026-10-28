package ru.rfsnab.authservice.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.rfsnab.authservice.exceptions.InvalidOAuth2CodeException;

import java.time.Clock;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Одноразовые коды обмена OAuth2: вместо токенов в URL фронт получает короткоживущий code
 * и меняет его на токены через POST /v1/auth/oauth2/exchange.
 */
@Service
@RequiredArgsConstructor
public class OAuth2CodeService {

    private static final long CODE_TTL_MS = 60_000L;
    private static final String INVALID_CODE_MESSAGE = "Код недействителен или истёк";

    private final Clock clock;

    // ponytail: in-memory, works only with a single auth-service instance; move to Redis if scaled
    private final Map<String, Entry> codes = new ConcurrentHashMap<>();

    public String issue(Map<String, Object> tokens) {
        long now = clock.millis();
        codes.values().removeIf(entry -> entry.expiresAtMs() <= now);
        String code = UUID.randomUUID().toString();
        codes.put(code, new Entry(tokens, now + CODE_TTL_MS));
        return code;
    }

    public Map<String, Object> exchange(String code) {
        Entry entry = code == null ? null : codes.remove(code);
        if (entry == null || entry.expiresAtMs() <= clock.millis()) {
            throw new InvalidOAuth2CodeException(INVALID_CODE_MESSAGE);
        }
        return entry.tokens();
    }

    private record Entry(Map<String, Object> tokens, long expiresAtMs) {}
}
