package ru.rfsnab.orderservice.delivery.models.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import ru.rfsnab.orderservice.delivery.models.entity.enums.CarrierCode;
import ru.rfsnab.orderservice.delivery.models.entity.enums.ShipmentMode;

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
