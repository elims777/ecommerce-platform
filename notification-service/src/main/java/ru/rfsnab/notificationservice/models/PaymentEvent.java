package ru.rfsnab.notificationservice.models;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record PaymentEvent(
        String eventType,   // "PAYMENT_STATUS_CHANGED"
        UUID orderId,
        String status,
        BigDecimal amount,
        String paymentMode,
        String customerEmail,
        LocalDateTime timestamp
) {}
