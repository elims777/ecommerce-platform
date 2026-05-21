package ru.rfsnab.paymentservice.models.dto.tochka;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;

public record TochkaStatusResponse(
        @JsonProperty("operationId") String operationId,
        @JsonProperty("status") String status,
        @JsonProperty("amount") BigDecimal amount
) {}
