package ru.rfsnab.integrationservice.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import ru.rfsnab.integrationservice.BaseIntegrationTest;
import ru.rfsnab.integrationservice.repository.ExchangeSessionRepository;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Интеграционные тесты протокола обмена CommerceML.
 * Проверяют полный цикл: checkauth → init → file.
 * import не тестируем через контроллер — product-service недоступен,
 * логика парсинга покрыта в CatalogImportServiceTest.
 */
@DisplayName("CommerceMLExchangeController")
class CommerceMLExchangeControllerTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ExchangeSessionRepository sessionRepository;

    @BeforeEach
    void cleanup() {
        sessionRepository.deleteAll();
    }

    private String basicAuth(String username, String password) {
        return "Basic " + Base64.getEncoder().encodeToString((username + ":" + password).getBytes());
    }

    /**
     * Выполняет checkauth и возвращает sessionId из ответа.
     */
    private String performCheckAuth() throws Exception {
        MvcResult result = mockMvc.perform(get("/1c-exchange")
                        .param("type", "catalog")
                        .param("mode", "checkauth")
                        .header("Authorization", basicAuth("test_user", "test_pass")))
                .andExpect(status().isOk())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        String[] lines = body.split("\n");
        assertThat(lines[0]).isEqualTo("success");
        return lines[2]; // третья строка — значение cookie
    }

    @Nested
    @DisplayName("checkauth")
    class CheckAuthTests {

        @Test
        @DisplayName("успешная авторизация возвращает success + cookie")
        void shouldAuthSuccessfully() throws Exception {
            MvcResult result = mockMvc.perform(get("/1c-exchange")
                            .param("type", "catalog")
                            .param("mode", "checkauth")
                            .header("Authorization", basicAuth("test_user", "test_pass")))
                    .andExpect(status().isOk())
                    .andExpect(header().exists("Set-Cookie"))
                    .andReturn();

            String body = result.getResponse().getContentAsString();
            String[] lines = body.split("\n");
            assertThat(lines).hasSizeGreaterThanOrEqualTo(3);
            assertThat(lines[0]).isEqualTo("success");
        }

        @Test
        @DisplayName("неверные credentials возвращают failure")
        void shouldRejectInvalidCredentials() throws Exception {
            mockMvc.perform(get("/1c-exchange")
                            .param("type", "catalog")
                            .param("mode", "checkauth")
                            .header("Authorization", basicAuth("wrong", "wrong")))
                    .andExpect(status().isOk())
                    .andExpect(content().string(org.hamcrest.Matchers.startsWith("failure")));
        }

        @Test
        @DisplayName("без Authorization header возвращает failure")
        void shouldRejectMissingAuth() throws Exception {
            mockMvc.perform(get("/1c-exchange")
                            .param("type", "catalog")
                            .param("mode", "checkauth"))
                    .andExpect(status().isOk())
                    .andExpect(content().string(org.hamcrest.Matchers.startsWith("failure")));
        }
    }

    @Nested
    @DisplayName("init")
    class InitTests {

        @Test
        @DisplayName("возвращает параметры обмена после авторизации")
        void shouldReturnExchangeParams() throws Exception {
            String sessionId = performCheckAuth();

            mockMvc.perform(get("/1c-exchange")
                            .param("type", "catalog")
                            .param("mode", "init")
                            .cookie(new jakarta.servlet.http.Cookie("exchange_session", sessionId)))
                    .andExpect(status().isOk())
                    .andExpect(content().string(org.hamcrest.Matchers.containsString("zip=no")))
                    .andExpect(content().string(org.hamcrest.Matchers.containsString("file_limit=")));
        }

        @Test
        @DisplayName("без cookie возвращает failure")
        void shouldRejectWithoutSession() throws Exception {
            mockMvc.perform(get("/1c-exchange")
                            .param("type", "catalog")
                            .param("mode", "init"))
                    .andExpect(status().isOk())
                    .andExpect(content().string(org.hamcrest.Matchers.startsWith("failure")));
        }
    }

    @Nested
    @DisplayName("file")
    class FileTests {

        @Test
        @DisplayName("принимает файл и возвращает success")
        void shouldAcceptFile() throws Exception {
            String sessionId = performCheckAuth();

            mockMvc.perform(post("/1c-exchange")
                            .param("type", "catalog")
                            .param("mode", "file")
                            .param("filename", "import.xml")
                            .cookie(new jakarta.servlet.http.Cookie("exchange_session", sessionId))
                            .contentType(MediaType.APPLICATION_OCTET_STREAM)
                            .content("<?xml version=\"1.0\"?><test/>"))
                    .andExpect(status().isOk())
                    .andExpect(content().string("success"));
        }

        @Test
        @DisplayName("без filename возвращает failure")
        void shouldRejectWithoutFilename() throws Exception {
            String sessionId = performCheckAuth();

            mockMvc.perform(post("/1c-exchange")
                            .param("type", "catalog")
                            .param("mode", "file")
                            .cookie(new jakarta.servlet.http.Cookie("exchange_session", sessionId))
                            .contentType(MediaType.APPLICATION_OCTET_STREAM)
                            .content("data"))
                    .andExpect(status().isOk())
                    .andExpect(content().string(org.hamcrest.Matchers.startsWith("failure")));
        }
    }

    @Nested
    @DisplayName("unknown type/mode")
    class ErrorTests {

        @Test
        @DisplayName("неизвестный type возвращает failure")
        void shouldRejectUnknownType() throws Exception {
            mockMvc.perform(get("/1c-exchange")
                            .param("type", "unknown")
                            .param("mode", "checkauth"))
                    .andExpect(status().isOk())
                    .andExpect(content().string(org.hamcrest.Matchers.startsWith("failure")));
        }

        @Test
        @DisplayName("неизвестный mode возвращает failure")
        void shouldRejectUnknownMode() throws Exception {
            String sessionId = performCheckAuth();

            mockMvc.perform(get("/1c-exchange")
                            .param("type", "catalog")
                            .param("mode", "unknown")
                            .cookie(new jakarta.servlet.http.Cookie("exchange_session", sessionId)))
                    .andExpect(status().isOk())
                    .andExpect(content().string(org.hamcrest.Matchers.startsWith("failure")));
        }
    }
}
