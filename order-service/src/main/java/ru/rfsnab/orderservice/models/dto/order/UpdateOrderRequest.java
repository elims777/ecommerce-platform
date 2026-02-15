package ru.rfsnab.orderservice.models.dto.order;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import ru.rfsnab.orderservice.models.entity.enums.DeliveryMethod;
import ru.rfsnab.orderservice.models.entity.enums.PaymentMethod;

import java.util.List;

/**
 * DTO для обновления заказа.
 * Разрешено только в статусе CREATED.
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

        @NotEmpty(message = "Список товаров обязателен")
        @Valid
        List<OrderItemDto> items,

        String comment
) implements HasDeliveryInfo {
}
