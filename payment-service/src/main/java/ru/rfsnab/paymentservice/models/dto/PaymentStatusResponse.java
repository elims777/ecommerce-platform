package ru.rfsnab.paymentservice.models.dto;

import ru.rfsnab.paymentservice.models.entity.enums.PaymentStatus;
import java.util.UUID;

public record PaymentStatusResponse(
        UUID orderId,
        PaymentStatus status,
        String paymentLink
) {}
