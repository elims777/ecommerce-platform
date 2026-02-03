package ru.rfsnab.orderservice.models.entity.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
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
    REFUNDED("Возврат средств");

    private final String displayName;
}
