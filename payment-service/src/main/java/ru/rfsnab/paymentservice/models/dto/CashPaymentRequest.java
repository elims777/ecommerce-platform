package ru.rfsnab.paymentservice.models.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

public record CashPaymentRequest(
        @NotNull @Positive BigDecimal amount
) {}
