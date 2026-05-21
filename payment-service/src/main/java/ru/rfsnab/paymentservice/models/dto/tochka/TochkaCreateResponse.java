package ru.rfsnab.paymentservice.models.dto.tochka;

import com.fasterxml.jackson.annotation.JsonProperty;

public record TochkaCreateResponse(
        @JsonProperty("operationId") String operationId,
        @JsonProperty("paymentLink") String paymentLink
) {}
