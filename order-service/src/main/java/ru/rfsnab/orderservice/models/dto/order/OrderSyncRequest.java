package ru.rfsnab.orderservice.models.dto.order;

import ru.rfsnab.orderservice.models.entity.enums.OrderStatus;

public record OrderSyncRequest(
        String externalId,
        OrderStatus newStatus
) {
}
