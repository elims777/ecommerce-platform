package ru.rfsnab.orderservice.delivery;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Внесение накладной к заказу. shipmentMode обязателен для первой накладной заказа;
 * для второй при режиме SINGLE нужно передать MULTIPLE (переключение).
 */
public record AddShipmentRequest(
        @NotNull CarrierCode carrierCode,
        @NotBlank @Size(max = 100) String waybillNumber,
        ShipmentMode shipmentMode
) {
}
