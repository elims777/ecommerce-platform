package ru.rfsnab.orderservice.delivery;

/**
 * Kafka-событие SHIPMENT_NOT_FOUND (топик order-events): накладная не найдена в ТК 24 часа — письмо менеджеру.
 *
 * @param notFoundSince момент первой неудачной проверки, ISO-8601
 */
public record ShipmentNotFoundEvent(String eventType, String orderNumber, String carrierName, String waybillNumber,
                                    String notFoundSince) {

    /** Тип события в поле eventType. */
    public static final String TYPE = "SHIPMENT_NOT_FOUND";

    /** Создаёт событие с типом {@link #TYPE}. */
    public static ShipmentNotFoundEvent of(String orderNumber, String carrierName, String waybillNumber,
                                           java.time.Instant notFoundSince) {
        return new ShipmentNotFoundEvent(TYPE, orderNumber, carrierName, waybillNumber, notFoundSince.toString());
    }
}
