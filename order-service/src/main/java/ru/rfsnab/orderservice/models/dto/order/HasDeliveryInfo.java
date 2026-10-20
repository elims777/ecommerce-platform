package ru.rfsnab.orderservice.models.dto.order;

import ru.rfsnab.orderservice.delivery.models.entity.enums.CarrierCode;
import ru.rfsnab.orderservice.models.entity.enums.DeliveryMethod;

/**
 * Общий контракт для DTO, содержащих информацию о доставке.
 * Sealed — только CreateOrderRequest и UpdateOrderRequest могут имплементировать.
 * Используется в OrderService.validateDeliveryInfo() для единой валидации.
 */
public sealed interface HasDeliveryInfo permits CreateOrderRequest, UpdateOrderRequest {
    DeliveryMethod deliveryMethod();

    AddressDto deliveryAddress();

    Long warehousePointId();

    /** ФИО получателя для CARRIER_TERMINAL; для остальных способов игнорируется. */
    String pickupRecipientName();

    /** Телефон получателя для CARRIER_TERMINAL; для остальных способов игнорируется. */
    String pickupRecipientPhone();

    /** ТК для CARRIER_ADDRESS и CARRIER_TERMINAL; для остальных способов игнорируется. */
    CarrierCode carrierCode();

    /** Терминал для CARRIER_TERMINAL (только ТК из carrierCode); для остальных способов игнорируется. Адрес для терминала не нужен. */
    Long carrierTerminalId();
}
