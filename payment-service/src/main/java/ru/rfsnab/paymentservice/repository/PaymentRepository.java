package ru.rfsnab.paymentservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.rfsnab.paymentservice.models.entity.Payment;
import ru.rfsnab.paymentservice.models.entity.enums.PaymentStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {
    Optional<Payment> findByOrderId(UUID orderId);
    List<Payment> findByStatusAndCreatedAtBefore(PaymentStatus status, LocalDateTime threshold);
}
