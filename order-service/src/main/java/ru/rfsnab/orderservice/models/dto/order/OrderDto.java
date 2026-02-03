package ru.rfsnab.orderservice.models.dto.order;

import ru.rfsnab.orderservice.models.entity.enums.DeliveryMethod;
import ru.rfsnab.orderservice.models.entity.enums.OrderStatus;
import ru.rfsnab.orderservice.models.entity.enums.PaymentMethod;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record OrderDto(
        UUID id,
        String orderNumber,
        OrderStatus status,
        String statusDisplayName,
        PaymentMethod paymentMethod,
        String paymentMethodDisplayName,
        DeliveryMethod deliveryMethod,
        String deliveryMethodDisplayName,
        List<OrderItemDto> items,
        AddressDto deliveryAddress,
        BigDecimal totalAmount,
        String trackingNumber,
        String comment,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
