package ru.rfsnab.paymentservice.models.dto.tochka;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.util.List;

public record TochkaCreateRequest(
        @JsonProperty("customerCode") String customerCode,
        @JsonProperty("amount") BigDecimal amount,
        @JsonProperty("purpose") String purpose,
        @JsonProperty("paymentMode") List<String> paymentMode,
        @JsonProperty("redirectUrl") String redirectUrl,
        @JsonProperty("failRedirectUrl") String failRedirectUrl,
        @JsonProperty("paymentLinkId") String paymentLinkId
) {}
