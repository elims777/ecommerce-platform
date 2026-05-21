package ru.rfsnab.paymentservice.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ru.rfsnab.paymentservice.models.entity.enums.PaymentStatus;
import ru.rfsnab.paymentservice.repository.PaymentRepository;
import ru.rfsnab.paymentservice.service.PaymentService;

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentStatusScheduler {

    private final PaymentRepository paymentRepository;
    private final PaymentService paymentService;

    private static final int PENDING_TTL_MINUTES = 10;

    @Scheduled(fixedDelay = 300_000)
    public void pollPendingPayments() {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(PENDING_TTL_MINUTES);
        var pendingPayments = paymentRepository.findByStatusAndCreatedAtBefore(PaymentStatus.PENDING, threshold);

        if (pendingPayments.isEmpty()) {
            return;
        }

        log.info("Polling {} pending payments older than {} minutes", pendingPayments.size(), PENDING_TTL_MINUTES);

        for (var payment : pendingPayments) {
            try {
                paymentService.updateStatusFromTochka(payment);
            } catch (Exception e) {
                log.error("Failed to update payment status: orderId={}, error={}", payment.getOrderId(), e.getMessage());
            }
        }
    }
}
