package ru.rfsnab.paymentservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.rfsnab.paymentservice.config.TochkaProperties;
import ru.rfsnab.paymentservice.exception.PaymentNotFoundException;
import ru.rfsnab.paymentservice.kafka.PaymentKafkaProducer;
import ru.rfsnab.paymentservice.models.dto.*;
import ru.rfsnab.paymentservice.models.dto.tochka.*;
import ru.rfsnab.paymentservice.models.entity.Payment;
import ru.rfsnab.paymentservice.models.entity.enums.PaymentMode;
import ru.rfsnab.paymentservice.models.entity.enums.PaymentStatus;
import ru.rfsnab.paymentservice.repository.PaymentRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final TochkaApiClient tochkaApiClient;
    private final PaymentKafkaProducer kafkaProducer;
    private final TochkaProperties tochkaProperties;

    @Transactional
    public PaymentResponse createPayment(CreatePaymentRequest request) {
        return paymentRepository.findByOrderId(request.orderId())
                .filter(p -> p.getStatus() == PaymentStatus.PENDING)
                .map(existing -> new PaymentResponse(
                        existing.getOrderId(), existing.getPaymentLink(),
                        existing.getOperationId(), existing.getStatus()))
                .orElseGet(() -> doCreatePayment(request));
    }

    private PaymentResponse doCreatePayment(CreatePaymentRequest request) {
        String orderId = request.orderId().toString();
        var tochkaRequest = new TochkaCreateRequest(
                new TochkaCreateRequest.Data(
                        tochkaProperties.getCustomerCode(),
                        request.amount(),
                        "Заказ #" + request.orderNumber(),
                        "SBP".equals(request.paymentMode()) ? List.of("sbp") : List.of("card"),
                        tochkaProperties.getMerchantId(),
                        tochkaProperties.getRedirectUrl() + "?orderId=" + orderId,
                        tochkaProperties.getFailRedirectUrl() + "?orderId=" + orderId,
                        orderId
                )
        );

        TochkaCreateResponse tochkaResponse = tochkaApiClient.createPayment(tochkaRequest);

        Payment payment = Payment.builder()
                .orderId(request.orderId())
                .operationId(tochkaResponse.data().operationId())
                .paymentLink(tochkaResponse.data().paymentUrl())
                .amount(request.amount())
                .status(PaymentStatus.PENDING)
                .paymentMode("SBP".equals(request.paymentMode()) ? PaymentMode.SBP : PaymentMode.CARD)
                .customerEmail(request.customerEmail())
                .createdAt(LocalDateTime.now())
                .build();

        paymentRepository.save(payment);
        log.info("Payment created: orderId={}, operationId={}", request.orderId(), tochkaResponse.data().operationId());

        return new PaymentResponse(payment.getOrderId(), payment.getPaymentLink(),
                payment.getOperationId(), payment.getStatus());
    }

    @Transactional
    public PaymentStatusResponse getStatus(UUID orderId) {
        Payment payment = findByOrderId(orderId);
        if (payment.getStatus() == PaymentStatus.PENDING && payment.getOperationId() != null) {
            updateStatusFromTochka(payment);
        }
        return new PaymentStatusResponse(payment.getOrderId(), payment.getStatus(), payment.getPaymentLink());
    }

    @Transactional
    public void recordCashPayment(UUID orderId, CashPaymentRequest request) {
        Payment payment = Payment.builder()
                .orderId(orderId)
                .amount(request.amount())
                .status(PaymentStatus.APPROVED)
                .paymentMode(PaymentMode.CASH)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        paymentRepository.save(payment);
        kafkaProducer.sendPaymentEvent(payment, PaymentStatus.APPROVED);
        log.info("Cash payment recorded: orderId={}", orderId);
    }

    @Transactional
    public void refundPayment(UUID orderId) {
        Payment payment = findByOrderId(orderId);
        if (payment.getStatus() != PaymentStatus.APPROVED) {
            throw new IllegalStateException(
                    "Refund only allowed for APPROVED payments, current: " + payment.getStatus());
        }
        tochkaApiClient.refundPayment(payment.getOperationId(),
                new TochkaRefundRequest(new TochkaRefundRequest.Data(payment.getAmount())));

        payment.setStatus(PaymentStatus.REFUNDED);
        payment.setUpdatedAt(LocalDateTime.now());
        paymentRepository.save(payment);
        kafkaProducer.sendPaymentEvent(payment, PaymentStatus.REFUNDED);
        log.info("Payment refunded: orderId={}", orderId);
    }

    public void updateStatusFromTochka(Payment payment) {
        TochkaStatusResponse tochkaStatus = tochkaApiClient.getPaymentStatus(payment.getOperationId());
        var operations = tochkaStatus.data() != null ? tochkaStatus.data().operation() : null;
        if (operations == null || operations.isEmpty()) return;
        PaymentStatus mapped = mapTochkaStatus(operations.get(0).status());

        if (mapped == null) {
            return;
        }

        payment.setStatus(mapped);
        payment.setUpdatedAt(LocalDateTime.now());
        paymentRepository.save(payment);
        kafkaProducer.sendPaymentEvent(payment, mapped);
    }

    public PaymentStatus mapTochkaStatus(String tochkaStatus) {
        return switch (tochkaStatus) {
            case "APPROVED"           -> PaymentStatus.APPROVED;
            case "EXPIRED"            -> PaymentStatus.FAILED;
            case "WAIT_FULL_PAYMENT"  -> PaymentStatus.APPROVED;
            case "REFUNDED", "REFUNDED_PARTIALLY", "ON_REFUND" -> PaymentStatus.REFUNDED;
            case "CREATED", "AUTHORIZED" -> null;
            default -> {
                log.warn("Unknown Tochka status: {}", tochkaStatus);
                yield null;
            }
        };
    }

    private Payment findByOrderId(UUID orderId) {
        return paymentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new PaymentNotFoundException("Payment not found for orderId: " + orderId));
    }
}
