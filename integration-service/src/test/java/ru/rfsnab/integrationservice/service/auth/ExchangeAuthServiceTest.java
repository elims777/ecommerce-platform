package ru.rfsnab.integrationservice.service.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import ru.rfsnab.integrationservice.BaseIntegrationTest;
import ru.rfsnab.integrationservice.model.ExchangeSession;
import ru.rfsnab.integrationservice.model.ExchangeSession.ExchangeType;
import ru.rfsnab.integrationservice.repository.ExchangeSessionRepository;

import java.util.Base64;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ExchangeAuthService")
class ExchangeAuthServiceTest extends BaseIntegrationTest {

    @Autowired
    private ExchangeAuthService authService;

    @Autowired
    private ExchangeSessionRepository sessionRepository;

    @BeforeEach
    void cleanup() {
        sessionRepository.deleteAll();
    }

    private String basicAuth(String username, String password) {
        return "Basic " + Base64.getEncoder().encodeToString((username + ":" + password).getBytes());
    }

    @Nested
    @DisplayName("authenticate")
    class AuthenticateTests {

        @Test
        @DisplayName("успешная авторизация с валидными credentials")
        void shouldAuthenticateWithValidCredentials() {
            Optional<String> sessionId = authService.authenticate(
                    basicAuth("test_user", "test_pass"), ExchangeType.CATALOG);

            assertThat(sessionId).isPresent();
            assertThat(sessionId.get()).isNotBlank();
        }

        @Test
        @DisplayName("создаёт сессию в БД при успешной авторизации")
        void shouldCreateSessionInDatabase() {
            Optional<String> sessionId = authService.authenticate(
                    basicAuth("test_user", "test_pass"), ExchangeType.CATALOG);

            assertThat(sessionId).isPresent();
            Optional<ExchangeSession> session = sessionRepository.findBySessionId(sessionId.get());
            assertThat(session).isPresent();
            assertThat(session.get().getExchangeType()).isEqualTo(ExchangeType.CATALOG);
        }

        @Test
        @DisplayName("отклоняет неверный пароль")
        void shouldRejectInvalidPassword() {
            Optional<String> sessionId = authService.authenticate(
                    basicAuth("test_user", "wrong_pass"), ExchangeType.CATALOG);

            assertThat(sessionId).isEmpty();
        }

        @Test
        @DisplayName("отклоняет неверный логин")
        void shouldRejectInvalidUsername() {
            Optional<String> sessionId = authService.authenticate(
                    basicAuth("wrong_user", "test_pass"), ExchangeType.CATALOG);

            assertThat(sessionId).isEmpty();
        }

        @Test
        @DisplayName("отклоняет null Authorization header")
        void shouldRejectNullHeader() {
            Optional<String> sessionId = authService.authenticate(null, ExchangeType.CATALOG);

            assertThat(sessionId).isEmpty();
        }

        @Test
        @DisplayName("отклоняет невалидный формат Basic auth")
        void shouldRejectInvalidFormat() {
            Optional<String> sessionId = authService.authenticate(
                    "Bearer some-token", ExchangeType.CATALOG);

            assertThat(sessionId).isEmpty();
        }
    }

    @Nested
    @DisplayName("validateSession")
    class ValidateSessionTests {

        @Test
        @DisplayName("валидирует существующую сессию")
        void shouldValidateExistingSession() {
            String sessionId = authService.authenticate(
                    basicAuth("test_user", "test_pass"), ExchangeType.CATALOG).orElseThrow();

            Optional<ExchangeSession> session = authService.validateSession(sessionId);

            assertThat(session).isPresent();
        }

        @Test
        @DisplayName("отклоняет несуществующую сессию")
        void shouldRejectNonExistentSession() {
            Optional<ExchangeSession> session = authService.validateSession("non-existent-id");

            assertThat(session).isEmpty();
        }

        @Test
        @DisplayName("отклоняет null cookie")
        void shouldRejectNullCookie() {
            Optional<ExchangeSession> session = authService.validateSession(null);

            assertThat(session).isEmpty();
        }
    }
}
