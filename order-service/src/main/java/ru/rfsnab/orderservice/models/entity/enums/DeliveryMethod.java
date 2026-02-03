package ru.rfsnab.orderservice.models.entity.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum DeliveryMethod {
    PICKUP("Самовывоз"),
    SUPPLIER_DELIVERY("Доставка поставщиком");

    private final String displayName;
}
