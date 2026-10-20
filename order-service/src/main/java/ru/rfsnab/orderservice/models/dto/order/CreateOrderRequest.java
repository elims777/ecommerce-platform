package ru.rfsnab.orderservice.models.dto.order;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import ru.rfsnab.orderservice.delivery.models.entity.enums.CarrierCode;
import ru.rfsnab.orderservice.models.entity.enums.DeliveryMethod;
import ru.rfsnab.orderservice.models.entity.enums.PaymentMethod;

/**
 * DTO для создания заказа из корзины.
 * Товары берутся из корзины пользователя.
 * Для PICKUP: warehousePointId + pickupRecipientName + pickupRecipientPhone обязательны.
 * Для CARRIER_ADDRESS: deliveryAddress + carrierCode обязательны. SUPPLIER_DELIVERY клиенту недоступен.
 * Для CARRIER_TERMINAL: carrierCode + carrierTerminalId обязательны; адрес не нужен, получатель — pickupRecipientName/pickupRecipientPhone.
 */
public record CreateOrderRequest(
        @NotNull(message = "Способ оплаты обязателен")
        PaymentMethod paymentMethod,

        @NotNull(message = "Способ доставки обязателен")
        DeliveryMethod deliveryMethod,

        AddressDto deliveryAddress,

        Long warehousePointId,

        @Size(max = 100, message = "Имя получателя не должно превышать 100 символов")
        String pickupRecipientName,

        @Size(max = 20, message = "Телефон получателя не должен превышать 20 символов")
        String pickupRecipientPhone,

        String comment,

        // B2B snapshot — required when clientType=B2B, validated in service
        String companyName,
        String inn,

        // Customer snapshot — имя и телефон заказчика (B2C и B2B)
        String customerName,
        String customerPhone,

        // ТК — обязательна для CARRIER_ADDRESS, проверяется в сервисе
        CarrierCode carrierCode,

        // Терминал ТК — обязателен для CARRIER_TERMINAL, проверяется в сервисе
        Long carrierTerminalId
) implements HasDeliveryInfo {

    /** Запрос без терминала ТК — для способов доставки, где терминал не выбирается. */
    public CreateOrderRequest(PaymentMethod paymentMethod, DeliveryMethod deliveryMethod, AddressDto deliveryAddress,
                              Long warehousePointId, String pickupRecipientName, String pickupRecipientPhone,
                              String comment, String companyName, String inn, String customerName,
                              String customerPhone, CarrierCode carrierCode) {
        this(paymentMethod, deliveryMethod, deliveryAddress, warehousePointId, pickupRecipientName,
                pickupRecipientPhone, comment, companyName, inn, customerName, customerPhone, carrierCode, null);
    }
}
