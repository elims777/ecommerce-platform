package ru.rfsnab.paymentservice.models.dto.tochka;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.util.List;

public record TochkaCreateRequest(
        @JsonProperty("Data") Data data
) {
    public record Data(
            @JsonProperty("customerCode") String customerCode,
            @JsonProperty("amount") BigDecimal amount,
            @JsonProperty("purpose") String purpose,
            @JsonProperty("paymentMode") List<String> paymentMode,
            @JsonProperty("merchantId") String merchantId,
            @JsonProperty("redirectUrl") String redirectUrl,
            @JsonProperty("failRedirectUrl") String failRedirectUrl,
            @JsonProperty("paymentLinkId") String paymentLinkId
    ) {}
}
