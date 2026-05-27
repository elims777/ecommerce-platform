package ru.rfsnab.orderservice.models.dto.payment;

import java.util.UUID;

public record PaymentStatusResponse(
        UUID orderId,
        String status,
        String paymentLink
) {}
