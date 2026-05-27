package ru.rfsnab.paymentservice.models.dto.tochka;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;

public record TochkaRefundRequest(
        @JsonProperty("Data") Data data
) {
    public record Data(
            @JsonProperty("amount") BigDecimal amount
    ) {}
}
