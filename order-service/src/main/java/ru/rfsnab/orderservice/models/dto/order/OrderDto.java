package ru.rfsnab.orderservice.models.dto.order;

import lombok.Builder;
import ru.rfsnab.orderservice.models.entity.enums.DeliveryMethod;
import ru.rfsnab.orderservice.models.entity.enums.OrderStatus;
import ru.rfsnab.orderservice.models.entity.enums.PaymentMethod;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Builder
public record OrderDto(
        UUID id,
        Long userId,
        String orderNumber,
        OrderStatus status,
        PaymentMethod paymentMethod,
        DeliveryMethod deliveryMethod,
        BigDecimal totalAmount,
        List<OrderItemDto> items,
        AddressDto deliveryAddress,
        WarehousePointDto warehousePoint,
        String trackingNumber,
        String customerEmail,
        String comment,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
