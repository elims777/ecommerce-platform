package ru.rfsnab.orderservice.models.entity.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
@JsonFormat(shape = JsonFormat.Shape.OBJECT)
public enum OrderStatus {
    CREATED("Заказ создан"),
    PROCESSING("В работе"),
    INVOICE_SENT("Счёт выставлен"),
    PENDING_PAYMENT("Ожидает оплаты"),
    AWAITING_CONFIRMATION("Ожидает подтверждения"),
    PAID("Оплачен"),
    PAYMENT_FAILED("Ошибка оплаты"),
    SHIPPED("Отгружен"),
    IN_TRANSIT("В пути"),
    DELIVERED("Доставлен"),
    CANCELLED("Отменён"),
    REFUNDED("Возврат средств"),
    PARTIALLY_PAID("Оплачен частично"),
    COMPLETED("Завершен");

    private final String displayName;

    /**
     * Сериализация: {"code": "CREATED", "displayName": "Заказ создан"}
     */
    @JsonProperty("code")
    public String getCode() {
        return name();
    }

    /**
     * Десериализация: принимает строку "CREATED" из request body.
     * Без этого @JsonFormat(OBJECT) сломает приём enum'ов в DTO.
     */
    @JsonCreator
    public static OrderStatus fromValue(String value) {
        return valueOf(value.toUpperCase());
    }

    public boolean isFinal() {
        return this == DELIVERED || this == CANCELLED || this == REFUNDED || this == COMPLETED;
    }

    public static java.util.List<OrderStatus> finalStatuses() {
        return java.util.List.of(DELIVERED, CANCELLED, REFUNDED, COMPLETED);
    }
}
