package ru.rfsnab.paymentservice.models.dto.tochka;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;

public record TochkaRefundRequest(
        @JsonProperty("amount") BigDecimal amount
) {}
