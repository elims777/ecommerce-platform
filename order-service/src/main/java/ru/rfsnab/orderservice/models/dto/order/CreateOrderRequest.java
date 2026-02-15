package ru.rfsnab.orderservice.models.dto.order;

import jakarta.validation.constraints.NotNull;
import ru.rfsnab.orderservice.models.entity.enums.DeliveryMethod;
import ru.rfsnab.orderservice.models.entity.enums.PaymentMethod;

/**
 * DTO для создания заказа из корзины.
 * Товары берутся из корзины пользователя.
 */
public record CreateOrderRequest(
        @NotNull(message = "Способ оплаты обязателен")
        PaymentMethod paymentMethod,

        @NotNull(message = "Способ доставки обязателен")
        DeliveryMethod deliveryMethod,

        AddressDto deliveryAddress,

        Long warehousePointId,

        String comment
) implements HasDeliveryInfo {
}
