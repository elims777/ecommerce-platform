package ru.rfsnab.paymentservice.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.rfsnab.paymentservice.config.TochkaProperties;
import ru.rfsnab.paymentservice.kafka.PaymentKafkaProducer;
import ru.rfsnab.paymentservice.models.dto.CashPaymentRequest;
import ru.rfsnab.paymentservice.models.dto.CreatePaymentRequest;
import ru.rfsnab.paymentservice.models.dto.PaymentResponse;
import ru.rfsnab.paymentservice.models.dto.tochka.TochkaCreateRequest;
import ru.rfsnab.paymentservice.models.dto.tochka.TochkaCreateResponse;
import ru.rfsnab.paymentservice.models.dto.tochka.TochkaStatusResponse;
import ru.rfsnab.paymentservice.models.entity.Payment;
import ru.rfsnab.paymentservice.models.entity.enums.PaymentMode;
import ru.rfsnab.paymentservice.models.entity.enums.PaymentStatus;
import ru.rfsnab.paymentservice.repository.PaymentRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock PaymentRepository paymentRepository;
    @Mock TochkaApiClient tochkaApiClient;
    @Mock PaymentKafkaProducer kafkaProducer;
    @Mock TochkaProperties tochkaProperties;

    PaymentService paymentService;

    @BeforeEach
    void setUp() {
        lenient().when(tochkaProperties.getCustomerCode()).thenReturn("CUST001");
        lenient().when(tochkaProperties.getMerchantId()).thenReturn("MERCH001");
        lenient().when(tochkaProperties.getRedirectUrl()).thenReturn("https://rfsnab.ru/payment/success");
        lenient().when(tochkaProperties.getFailRedirectUrl()).thenReturn("https://rfsnab.ru/payment/fail");
        paymentService = new PaymentService(paymentRepository, tochkaApiClient, kafkaProducer, tochkaProperties);
    }

    @Test
    void createPayment_card_savesPaymentAndReturnsLink() {
        UUID orderId = UUID.randomUUID();
        var request = new CreatePaymentRequest(orderId, BigDecimal.valueOf(5000), "ORD-00001", "user@test.com", "CARD");

        when(paymentRepository.findByOrderId(orderId)).thenReturn(Optional.empty());
        when(tochkaApiClient.createPayment(any())).thenReturn(
                new TochkaCreateResponse(new TochkaCreateResponse.Data("op-1", "https://pay.link/card")));
        when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PaymentResponse result = paymentService.createPayment(request);

        assertThat(result.paymentLink()).isEqualTo("https://pay.link/card");
        assertThat(result.operationId()).isEqualTo("op-1");
        assertThat(result.status()).isEqualTo(PaymentStatus.PENDING);

        ArgumentCaptor<TochkaCreateRequest> tochkaCaptor =
                ArgumentCaptor.forClass(TochkaCreateRequest.class);
        verify(tochkaApiClient).createPayment(tochkaCaptor.capture());
        assertThat(tochkaCaptor.getValue().data().paymentMode()).containsExactly("card");
        assertThat(tochkaCaptor.getValue().data().merchantId()).isEqualTo("MERCH001");

        verify(paymentRepository).save(any(Payment.class));
    }

    @Test
    void createPayment_sbp_sendsSbpModeToTochkaAndSavesSbpMode() {
        UUID orderId = UUID.randomUUID();
        var request = new CreatePaymentRequest(orderId, BigDecimal.valueOf(3000), "ORD-00002", "user@test.com", "SBP");

        when(paymentRepository.findByOrderId(orderId)).thenReturn(Optional.empty());
        when(tochkaApiClient.createPayment(any())).thenReturn(
                new TochkaCreateResponse(new TochkaCreateResponse.Data("op-sbp", "https://pay.link/sbp")));
        when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PaymentResponse result = paymentService.createPayment(request);

        assertThat(result.paymentLink()).isEqualTo("https://pay.link/sbp");

        ArgumentCaptor<TochkaCreateRequest> tochkaCaptor =
                ArgumentCaptor.forClass(TochkaCreateRequest.class);
        verify(tochkaApiClient).createPayment(tochkaCaptor.capture());
        assertThat(tochkaCaptor.getValue().data().paymentMode()).containsExactly("sbp");

        ArgumentCaptor<Payment> paymentCaptor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(paymentCaptor.capture());
        assertThat(paymentCaptor.getValue().getPaymentMode()).isEqualTo(PaymentMode.SBP);
    }

    @Test
    void createPayment_idempotent_returnExistingPendingPayment() {
        UUID orderId = UUID.randomUUID();
        var request = new CreatePaymentRequest(orderId, BigDecimal.valueOf(5000), "ORD-00001", "user@test.com", "CARD");
        var existing = Payment.builder()
                .orderId(orderId)
                .paymentLink("https://existing.link")
                .operationId("op-existing")
                .status(PaymentStatus.PENDING)
                .amount(BigDecimal.valueOf(5000))
                .createdAt(LocalDateTime.now())
                .build();

        when(paymentRepository.findByOrderId(orderId)).thenReturn(Optional.of(existing));

        PaymentResponse result = paymentService.createPayment(request);

        assertThat(result.paymentLink()).isEqualTo("https://existing.link");
        verifyNoInteractions(tochkaApiClient);
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void updateStatusFromTochka_approved_publishesKafkaEvent() {
        UUID orderId = UUID.randomUUID();
        var payment = Payment.builder()
                .orderId(orderId)
                .operationId("op-1")
                .status(PaymentStatus.PENDING)
                .amount(BigDecimal.valueOf(5000))
                .paymentMode(PaymentMode.CARD)
                .createdAt(LocalDateTime.now())
                .build();

        when(tochkaApiClient.getPaymentStatus("op-1"))
                .thenReturn(new TochkaStatusResponse(new TochkaStatusResponse.Data(
                        List.of(new TochkaStatusResponse.Operation("op-1", "APPROVED", BigDecimal.valueOf(5000))))));
        when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        paymentService.updateStatusFromTochka(payment);

        ArgumentCaptor<Payment> captor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(PaymentStatus.APPROVED);
        verify(kafkaProducer).sendPaymentEvent(any(), eq(PaymentStatus.APPROVED));
    }

    @Test
    void recordCashPayment_savesApprovedAndPublishesEvent() {
        UUID orderId = UUID.randomUUID();
        var request = new CashPaymentRequest(BigDecimal.valueOf(3000));
        when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        paymentService.recordCashPayment(orderId, request);

        ArgumentCaptor<Payment> captor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(PaymentStatus.APPROVED);
        assertThat(captor.getValue().getPaymentMode()).isEqualTo(PaymentMode.CASH);
        verify(kafkaProducer).sendPaymentEvent(any(), eq(PaymentStatus.APPROVED));
    }

    @Test
    void mapTochkaStatus_allMappings() {
        assertThat(paymentService.mapTochkaStatus("APPROVED")).isEqualTo(PaymentStatus.APPROVED);
        assertThat(paymentService.mapTochkaStatus("EXPIRED")).isEqualTo(PaymentStatus.FAILED);
        assertThat(paymentService.mapTochkaStatus("WAIT_FULL_PAYMENT")).isEqualTo(PaymentStatus.APPROVED);
        assertThat(paymentService.mapTochkaStatus("REFUNDED")).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(paymentService.mapTochkaStatus("REFUNDED_PARTIALLY")).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(paymentService.mapTochkaStatus("CREATED")).isNull();
        assertThat(paymentService.mapTochkaStatus("AUTHORIZED")).isNull();
    }
}
