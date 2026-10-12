package ru.rfsnab.orderservice.models.dto.order;

import jakarta.validation.constraints.NotNull;
import ru.rfsnab.orderservice.delivery.CarrierCode;
import ru.rfsnab.orderservice.models.entity.enums.DeliveryMethod;
import ru.rfsnab.orderservice.models.entity.enums.PaymentMethod;

/**
 * DTO для создания заказа из корзины.
 * Товары берутся из корзины пользователя.
 * Для PICKUP: warehousePointId + pickupRecipientName + pickupRecipientPhone обязательны.
 * Для CARRIER_ADDRESS: deliveryAddress + carrierCode обязательны. SUPPLIER_DELIVERY клиенту недоступен.
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
        String inn,

        // Customer snapshot — имя и телефон заказчика (B2C и B2B)
        String customerName,
        String customerPhone,

        // ТК — обязательна для CARRIER_ADDRESS, проверяется в сервисе
        CarrierCode carrierCode
) implements HasDeliveryInfo {
}
