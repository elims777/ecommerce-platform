package ru.rfsnab.paymentservice.models.dto.event;

import ru.rfsnab.paymentservice.models.entity.enums.PaymentMode;
import ru.rfsnab.paymentservice.models.entity.enums.PaymentStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record PaymentEvent(
        String eventType,       // always "PAYMENT_STATUS_CHANGED" — required by KafkaListenerService
        UUID orderId,
        PaymentStatus status,
        BigDecimal amount,
        PaymentMode paymentMode,
        String customerEmail,
        LocalDateTime timestamp
) {}
