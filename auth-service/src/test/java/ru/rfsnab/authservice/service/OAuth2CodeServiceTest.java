package ru.rfsnab.authservice.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.rfsnab.authservice.exceptions.InvalidOAuth2CodeException;

import java.time.Clock;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("OAuth2CodeService unit tests")
class OAuth2CodeServiceTest {

    private static final long START_MS = 1_000_000L;
    private static final long TTL_MS = 60_000L;

    private Clock clock;
    private OAuth2CodeService service;
    private final Map<String, Object> tokens = Map.of("access_token", "a", "refresh_token", "r");

    @BeforeEach
    void setUp() {
        clock = mock(Clock.class);
        when(clock.millis()).thenReturn(START_MS);
        service = new OAuth2CodeService(clock);
    }

    @Test
    @DisplayName("exchange() - выданный код возвращает те же токены")
    void exchange_IssuedCode_ReturnsTokens() {
        String code = service.issue(tokens);

        assertThat(service.exchange(code)).isEqualTo(tokens);
    }

    @Test
    @DisplayName("exchange() - повторный обмен того же кода → InvalidOAuth2CodeException")
    void exchange_SecondUse_Throws() {
        String code = service.issue(tokens);
        service.exchange(code);

        assertThatThrownBy(() -> service.exchange(code)).isInstanceOf(InvalidOAuth2CodeException.class);
    }

    @Test
    @DisplayName("exchange() - истёкший код → InvalidOAuth2CodeException")
    void exchange_Expired_Throws() {
        String code = service.issue(tokens);
        when(clock.millis()).thenReturn(START_MS + TTL_MS);

        assertThatThrownBy(() -> service.exchange(code)).isInstanceOf(InvalidOAuth2CodeException.class);
    }

    @Test
    @DisplayName("exchange() - неизвестный код и null → InvalidOAuth2CodeException")
    void exchange_UnknownOrNull_Throws() {
        assertThatThrownBy(() -> service.exchange("unknown")).isInstanceOf(InvalidOAuth2CodeException.class);
        assertThatThrownBy(() -> service.exchange(null)).isInstanceOf(InvalidOAuth2CodeException.class);
    }
}
