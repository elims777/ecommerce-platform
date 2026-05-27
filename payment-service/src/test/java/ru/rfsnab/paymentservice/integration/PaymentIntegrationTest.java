package ru.rfsnab.paymentservice.integration;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import ru.rfsnab.paymentservice.models.entity.Payment;
import ru.rfsnab.paymentservice.models.entity.enums.PaymentMode;
import ru.rfsnab.paymentservice.models.entity.enums.PaymentStatus;
import ru.rfsnab.paymentservice.repository.PaymentRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import com.github.tomakehurst.wiremock.client.WireMock;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
@EmbeddedKafka(partitions = 1, topics = {"payment.processed"})
class PaymentIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("payment_test")
            .withUsername("user")
            .withPassword("secret");

    static WireMockServer wireMock;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("tochka.base-url", () -> "http://localhost:" + wireMock.port());
    }

    @BeforeAll
    static void startWireMock() {
        wireMock = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        wireMock.start();
    }

    @AfterAll
    static void stopWireMock() {
        wireMock.stop();
    }

    @AfterEach
    void resetWireMock() {
        wireMock.resetAll();
    }

    @Autowired MockMvc mockMvc;
    @Autowired PaymentRepository paymentRepository;

    @BeforeEach
    void cleanDb() {
        paymentRepository.deleteAll();
    }

    @Test
    void createPayment_callsTochkaAndSavesPayment() throws Exception {
        UUID orderId = UUID.randomUUID();
        wireMock.stubFor(WireMock.post(WireMock.urlEqualTo("/acquiring/v1.0/payments"))
                .willReturn(WireMock.okJson("{\"Data\":{\"operationId\":\"op-111\",\"paymentUrl\":\"https://pay.link/abc\"}}")));

        mockMvc.perform(post("/api/v1/payments")
                        .header("X-Internal-Token", "test-secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                              "orderId": "%s",
                              "amount": 1500.00,
                              "orderNumber": "ORD-00001",
                              "customerEmail": "user@test.com",
                              "paymentMode": "CARD"
                            }
                            """.formatted(orderId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.paymentLink").value("https://pay.link/abc"))
                .andExpect(jsonPath("$.status").value("PENDING"));

        assertThat(paymentRepository.findByOrderId(orderId)).isPresent();
    }

    @Test
    void createPayment_missingToken_returns403() throws Exception {
        mockMvc.perform(post("/api/v1/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\":\"" + UUID.randomUUID() + "\",\"amount\":100,\"orderNumber\":\"ORD-1\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void createPayment_idempotent_existingPendingReturnsExistingLink() throws Exception {
        UUID orderId = UUID.randomUUID();
        Payment existing = Payment.builder()
                .orderId(orderId)
                .operationId("op-existing")
                .paymentLink("https://existing.link")
                .amount(BigDecimal.valueOf(1000))
                .status(PaymentStatus.PENDING)
                .paymentMode(PaymentMode.CARD)
                .createdAt(LocalDateTime.now())
                .build();
        paymentRepository.save(existing);

        mockMvc.perform(post("/api/v1/payments")
                        .header("X-Internal-Token", "test-secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\":\"%s\",\"amount\":1000,\"orderNumber\":\"ORD-1\",\"paymentMode\":\"CARD\"}".formatted(orderId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.paymentLink").value("https://existing.link"));

        wireMock.verify(0, WireMock.postRequestedFor(WireMock.urlEqualTo("/acquiring/v1.0/payments")));
    }

    @Test
    void getStatus_pendingPayment_pollsTochkaAndReturnsApproved() throws Exception {
        UUID orderId = UUID.randomUUID();
        Payment payment = Payment.builder()
                .orderId(orderId)
                .operationId("op-222")
                .paymentLink("https://pay.link/222")
                .amount(BigDecimal.valueOf(2000))
                .status(PaymentStatus.PENDING)
                .paymentMode(PaymentMode.CARD)
                .createdAt(LocalDateTime.now().minusMinutes(15))
                .build();
        paymentRepository.save(payment);

        wireMock.stubFor(WireMock.get(WireMock.urlEqualTo("/acquiring/v1.0/payments/op-222"))
                .willReturn(WireMock.okJson("{\"Data\":{\"Operation\":[{\"operationId\":\"op-222\",\"status\":\"APPROVED\",\"amount\":2000}]}}")));

        mockMvc.perform(get("/api/v1/payments/{orderId}/status", orderId)
                        .header("X-Internal-Token", "test-secret"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));

        assertThat(paymentRepository.findByOrderId(orderId).get().getStatus()).isEqualTo(PaymentStatus.APPROVED);
    }

    @Test
    void recordCashPayment_savesApproved() throws Exception {
        UUID orderId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/payments/{orderId}/cash", orderId)
                        .header("X-Internal-Token", "test-secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":5000}"))
                .andExpect(status().isOk());

        Payment saved = paymentRepository.findByOrderId(orderId).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(PaymentStatus.APPROVED);
        assertThat(saved.getPaymentMode()).isEqualTo(PaymentMode.CASH);
    }

    @Test
    void createPayment_sbp_savesPaymentWithSbpMode() throws Exception {
        UUID orderId = UUID.randomUUID();
        wireMock.stubFor(WireMock.post(WireMock.urlEqualTo("/acquiring/v1.0/payments"))
                .willReturn(WireMock.okJson("{\"Data\":{\"operationId\":\"op-sbp\",\"paymentUrl\":\"https://pay.link/sbp\"}}")));

        mockMvc.perform(post("/api/v1/payments")
                        .header("X-Internal-Token", "test-secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                              "orderId": "%s",
                              "amount": 2500.00,
                              "orderNumber": "ORD-00002",
                              "customerEmail": "user@test.com",
                              "paymentMode": "SBP"
                            }
                            """.formatted(orderId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.paymentLink").value("https://pay.link/sbp"))
                .andExpect(jsonPath("$.status").value("PENDING"));

        Payment saved = paymentRepository.findByOrderId(orderId).orElseThrow();
        assertThat(saved.getPaymentMode()).isEqualTo(PaymentMode.SBP);

        wireMock.verify(WireMock.postRequestedFor(WireMock.urlEqualTo("/acquiring/v1.0/payments"))
                .withRequestBody(WireMock.containing("\"sbp\"")));
    }

    @Test
    void refundPayment_callsTochkaAndSetsRefunded() throws Exception {
        UUID orderId = UUID.randomUUID();
        Payment payment = Payment.builder()
                .orderId(orderId)
                .operationId("op-333")
                .paymentLink("https://pay.link/333")
                .amount(BigDecimal.valueOf(3000))
                .status(PaymentStatus.APPROVED)
                .paymentMode(PaymentMode.CARD)
                .createdAt(LocalDateTime.now())
                .build();
        paymentRepository.save(payment);

        wireMock.stubFor(WireMock.post(WireMock.urlEqualTo("/acquiring/v1.0/payments/op-333/refund"))
                .willReturn(WireMock.ok()));

        mockMvc.perform(post("/api/v1/payments/{orderId}/refund", orderId)
                        .header("X-Internal-Token", "test-secret"))
                .andExpect(status().isOk());

        assertThat(paymentRepository.findByOrderId(orderId).get().getStatus()).isEqualTo(PaymentStatus.REFUNDED);
    }

    @Test
    void createPayment_invalidMode_returns400() throws Exception {
        UUID orderId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/payments")
                        .header("X-Internal-Token", "test-secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                              "orderId": "%s",
                              "amount": 1500.00,
                              "orderNumber": "ORD-BAD",
                              "paymentMode": "CASH_ON_DELIVERY"
                            }
                            """.formatted(orderId)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createPayment_nullCustomerEmail_succeeds() throws Exception {
        UUID orderId = UUID.randomUUID();
        wireMock.stubFor(WireMock.post(WireMock.urlEqualTo("/acquiring/v1.0/payments"))
                .willReturn(WireMock.okJson(
                        "{\"Data\":{\"operationId\":\"op-noemail\",\"paymentUrl\":\"https://pay.link/noemail\"}}")));

        mockMvc.perform(post("/api/v1/payments")
                        .header("X-Internal-Token", "test-secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                              "orderId": "%s",
                              "amount": 1000.00,
                              "orderNumber": "ORD-NOEMAIL",
                              "paymentMode": "CARD"
                            }
                            """.formatted(orderId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.paymentLink").value("https://pay.link/noemail"));
    }

    @Test
    void refundPayment_notApproved_returns500() throws Exception {
        UUID orderId = UUID.randomUUID();
        Payment pendingPayment = Payment.builder()
                .orderId(orderId)
                .operationId("op-pending")
                .paymentLink("https://pay.link/pending")
                .amount(BigDecimal.valueOf(1000))
                .status(PaymentStatus.PENDING)
                .paymentMode(PaymentMode.CARD)
                .createdAt(LocalDateTime.now())
                .build();
        paymentRepository.save(pendingPayment);

        mockMvc.perform(post("/api/v1/payments/{orderId}/refund", orderId)
                        .header("X-Internal-Token", "test-secret"))
                .andExpect(status().is5xxServerError());
    }
}
