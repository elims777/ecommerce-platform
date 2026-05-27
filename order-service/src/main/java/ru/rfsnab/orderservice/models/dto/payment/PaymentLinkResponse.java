package ru.rfsnab.orderservice.models.dto.payment;

public record PaymentLinkResponse(
        String paymentLink,
        String operationId,
        String status
) {}
