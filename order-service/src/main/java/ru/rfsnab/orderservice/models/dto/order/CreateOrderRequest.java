package ru.rfsnab.orderservice.models.dto.order;

import jakarta.validation.constraints.NotNull;
import ru.rfsnab.orderservice.models.entity.enums.DeliveryMethod;
import ru.rfsnab.orderservice.models.entity.enums.PaymentMethod;

/**
 * DTO для создания заказа из корзины.
 * Товары берутся из корзины пользователя.
 * Для PICKUP: warehousePointId + pickupRecipientName + pickupRecipientPhone обязательны.
 * Для DELIVERY: deliveryAddress обязателен.
 */
public record CreateOrderRequest(
        @NotNull(message = "Способ оплаты обязателен")
        PaymentMethod paymentMethod,

        @NotNull(message = "Способ доставки обязателен")
        DeliveryMethod deliveryMethod,

        AddressDto deliveryAddress,

        Long warehousePointId,

        String pickupRecipientName,

        String pickupRecipientPhone,

        String comment,

        // B2B snapshot — required when clientType=B2B, validated in service
        String companyName,
        String inn
) implements HasDeliveryInfo {
}
