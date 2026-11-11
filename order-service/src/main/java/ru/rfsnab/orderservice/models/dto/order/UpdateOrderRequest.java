package ru.rfsnab.orderservice.models.dto.order;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import ru.rfsnab.orderservice.delivery.models.entity.enums.CarrierCode;
import ru.rfsnab.orderservice.models.entity.enums.DeliveryMethod;
import ru.rfsnab.orderservice.models.entity.enums.PaymentMethod;

import java.util.List;

/**
 * DTO для обновления заказа.
 * Разрешено в статусах CREATED и PROCESSING.
 */
@Builder
public record UpdateOrderRequest(
        @NotNull(message = "Способ оплаты обязателен")
        PaymentMethod paymentMethod,

        @NotNull(message = "Способ доставки обязателен")
        DeliveryMethod deliveryMethod,

        @Valid
        AddressDto deliveryAddress,

        Long warehousePointId,

        @Size(max = 100, message = "Имя получателя не должно превышать 100 символов")
        String pickupRecipientName,

        @Pattern(regexp = "^\\+7\\d{10}$", message = "Телефон получателя должен быть в формате +7XXXXXXXXXX")
        String pickupRecipientPhone,

        CarrierCode carrierCode,

        Long carrierTerminalId,

        @NotNull(message = "Выберите город отгрузки")
        Long shippingWarehouseId,

        @NotEmpty(message = "Список товаров обязателен")
        @Valid
        List<OrderItemDto> items,

        String comment
) implements HasDeliveryInfo {
}
