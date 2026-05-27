package ru.rfsnab.paymentservice.models.dto.tochka;

import com.fasterxml.jackson.annotation.JsonProperty;

public record TochkaCreateResponse(
        @JsonProperty("Data") Data data
) {
    public record Data(
            @JsonProperty("operationId") String operationId,
            @JsonProperty("paymentUrl") String paymentUrl
    ) {}
}
