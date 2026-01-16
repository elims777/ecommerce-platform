package ru.rfsnab.notificationservice.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import ru.rfsnab.notificationservice.models.EmailVerificationToken;
import ru.rfsnab.notificationservice.repository.EmailVerificationTokenRepository;
import ru.rfsnab.notificationservice.service.EmailService;

import java.time.LocalDateTime;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@EmbeddedKafka(
        partitions = 1,
        brokerProperties = {"listeners=PLAINTEXT://localhost:9092", "port=9092"}
)
@DisplayName("VerificationController Integration Tests")
class VerificationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EmailVerificationTokenRepository tokenRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private EmailService emailService; // мокаем чтобы не отправлять реальные email

    @BeforeEach
    void setUp() {
        tokenRepository.deleteAll();
    }

    private EmailVerificationToken createAndSaveToken(String tokenValue, boolean verified, boolean expired) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiresAt = expired ? now.minusHours(1) : now.plusHours(1);

        EmailVerificationToken token = EmailVerificationToken.builder()
                .token(tokenValue)
                .userId(100L)
                .email("test@example.com")
                .createdAt(now.minusMinutes(30))
                .expiresAt(expiresAt)
                .verified(verified)
                .build();

        return tokenRepository.save(token);
    }

    @Nested
    @DisplayName("POST /api/verification/verify")
    class VerifyEndpointTests {

        @Test
        @DisplayName("валидный токен → 200 OK с данными пользователя")
        void verify_ValidToken_ReturnsOkWithUserData() throws Exception {
            // Given
            createAndSaveToken("valid-token-123", false, false);

            // When & Then
            mockMvc.perform(post("/api/verification/verify")
                            .param("token", "valid-token-123"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.valid", is(true)))
                    .andExpect(jsonPath("$.userId", is(100)))
                    .andExpect(jsonPath("$.email", is("test@example.com")))
                    .andExpect(jsonPath("$.message", is("Email verified successfully")));
        }

        @Test
        @DisplayName("несуществующий токен → 400 Bad Request")
        void verify_NonExistingToken_ReturnsBadRequest() throws Exception {
            // When & Then
            mockMvc.perform(post("/api/verification/verify")
                            .param("token", "non-existing-token"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.valid", is(false)))
                    .andExpect(jsonPath("$.message", is("Token not found")));
        }

        @Test
        @DisplayName("уже верифицированный токен → 400 Bad Request")
        void verify_AlreadyVerifiedToken_ReturnsBadRequest() throws Exception {
            // Given
            createAndSaveToken("verified-token", true, false);

            // When & Then
            mockMvc.perform(post("/api/verification/verify")
                            .param("token", "verified-token"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.valid", is(false)))
                    .andExpect(jsonPath("$.message", is("Email already verified")));
        }

        @Test
        @DisplayName("истёкший токен → 400 Bad Request")
        void verify_ExpiredToken_ReturnsBadRequest() throws Exception {
            // Given
            createAndSaveToken("expired-token", false, true);

            // When & Then
            mockMvc.perform(post("/api/verification/verify")
                            .param("token", "expired-token"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.valid", is(false)))
                    .andExpect(jsonPath("$.message", is("Token expired")));
        }

        @Test
        @DisplayName("отсутствует параметр token → 400 Bad Request")
        void verify_MissingTokenParam_ReturnsBadRequest() throws Exception {
            // When & Then
            mockMvc.perform(post("/api/verification/verify"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("повторная верификация того же токена → 400 Bad Request")
        void verify_DoubleVerification_SecondCallReturnsBadRequest() throws Exception {
            // Given
            createAndSaveToken("once-valid-token", false, false);

            // First verification - should succeed
            mockMvc.perform(post("/api/verification/verify")
                            .param("token", "once-valid-token"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.valid", is(true)));

            // Second verification - should fail
            mockMvc.perform(post("/api/verification/verify")
                            .param("token", "once-valid-token"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.valid", is(false)))
                    .andExpect(jsonPath("$.message", is("Email already verified")));
        }
    }
}
