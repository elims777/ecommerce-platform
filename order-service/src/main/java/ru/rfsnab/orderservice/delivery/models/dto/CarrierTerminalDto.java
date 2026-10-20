package ru.rfsnab.orderservice.delivery.models.dto;

import ru.rfsnab.orderservice.delivery.models.entity.enums.CarrierCode;
import ru.rfsnab.orderservice.delivery.models.entity.enums.PointType;

/**
 * Точка выдачи ТК для клиента (карта и список при оформлении заказа).
 */
public record CarrierTerminalDto(
        Long id,
        CarrierCode carrierCode,
        PointType pointType,
        String city,
        String name,
        String address,
        double latitude,
        double longitude,
        String workingHours,
        String phone
) {
}
