package ru.rfsnab.paymentservice.models.dto.tochka;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.util.List;

public record TochkaStatusResponse(
        @JsonProperty("Data") Data data
) {
    public record Data(
            @JsonProperty("Operation") List<Operation> operation
    ) {}

    public record Operation(
            @JsonProperty("operationId") String operationId,
            @JsonProperty("status") String status,
            @JsonProperty("amount") BigDecimal amount
    ) {}
}
