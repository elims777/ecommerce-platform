package ru.rfsnab.orderservice.models.dto.order;

import ru.rfsnab.orderservice.models.entity.enums.DeliveryMethod;

/**
 * Общий контракт для DTO, содержащих информацию о доставке.
 * Sealed — только CreateOrderRequest и UpdateOrderRequest могут имплементировать.
 * Используется в OrderService.validateDeliveryInfo() для единой валидации.
 */
public sealed interface HasDeliveryInfo permits CreateOrderRequest, UpdateOrderRequest {
    DeliveryMethod deliveryMethod();

    AddressDto deliveryAddress();

    Long warehousePointId();
}
