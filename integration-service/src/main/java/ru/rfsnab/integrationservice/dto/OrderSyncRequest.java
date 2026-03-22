package ru.rfsnab.integrationservice.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * DTO для PATCH /api/v1/orders/{orderId}/1c-sync в order-service.
 * Зеркало OrderSyncRequest из order-service.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class OrderSyncRequest {
    private String externalId;
    private String newStatus;
}
