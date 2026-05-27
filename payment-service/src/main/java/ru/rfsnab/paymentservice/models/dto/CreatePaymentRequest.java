package ru.rfsnab.paymentservice.models.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.util.UUID;

public record CreatePaymentRequest(
        @NotNull UUID orderId,
        @NotNull @Positive BigDecimal amount,
        @NotNull String orderNumber,
        String customerEmail,
        @Pattern(regexp = "^(CARD|SBP)$", message = "paymentMode must be CARD or SBP")
        String paymentMode
) {}
