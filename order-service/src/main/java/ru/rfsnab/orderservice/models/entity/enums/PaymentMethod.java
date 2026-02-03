package ru.rfsnab.orderservice.models.entity.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum PaymentMethod {
    CARD("Банковская карта"),
    SBP("Система быстрых платежей"),
    CASH_ON_DELIVERY("Оплата при получении");

    private final String displayName;
}
