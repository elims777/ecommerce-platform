package ru.rfsnab.orderservice.models.dto.order;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import ru.rfsnab.orderservice.models.entity.enums.DeliveryMethod;
import ru.rfsnab.orderservice.models.entity.enums.PaymentMethod;

public record CreateOrderRequest(
        @NotNull(message = "Payment method is required")
        PaymentMethod paymentMethod,

        @NotNull(message = "Delivery method is required")
        DeliveryMethod deliveryMethod,

        @Valid
        AddressDto deliveryAddress,

        Long warehousePointId,

        String comment
) {
}
