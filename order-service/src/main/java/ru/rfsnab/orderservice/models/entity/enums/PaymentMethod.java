package ru.rfsnab.orderservice.models.entity.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
@JsonFormat(shape = JsonFormat.Shape.OBJECT)
public enum PaymentMethod {
    CARD("Банковская карта"),
    SBP("Система быстрых платежей"),
    CASH_ON_DELIVERY("Оплата при получении");

    private final String displayName;

    @JsonProperty("code")
    public String getCode() {
        return name();
    }

    @JsonCreator
    public static PaymentMethod fromValue(String value) {
        return valueOf(value.toUpperCase());
    }
}
