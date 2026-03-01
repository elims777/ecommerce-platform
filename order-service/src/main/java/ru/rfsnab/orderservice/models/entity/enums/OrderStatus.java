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
    PENDING_PAYMENT("Ожидает оплаты"),
    PAID("Оплачен"),
    PAYMENT_FAILED("Ошибка оплаты"),
    PROCESSING("В обработке"),
    SHIPPED("Отправлен"),
    IN_TRANSIT("В пути"),
    DELIVERED("Доставлен"),
    CANCELLED("Отменён"),
    REFUNDED("Возврат средств"),
    AWAITING_CONFIRMATION("Ожидает подтверждения менеджера");

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
}
