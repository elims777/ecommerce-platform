package ru.rfsnab.paymentservice.models.dto;

import ru.rfsnab.paymentservice.models.entity.enums.PaymentStatus;
import java.util.UUID;

public record PaymentResponse(
        UUID orderId,
        String paymentLink,
        String operationId,
        PaymentStatus status
) {}
