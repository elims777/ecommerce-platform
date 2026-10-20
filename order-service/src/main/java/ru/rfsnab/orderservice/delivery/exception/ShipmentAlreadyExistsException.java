package ru.rfsnab.orderservice.delivery.exception;

/** Накладная этой ТК уже есть в журнале (unique carrier_code + waybill_number). */
public class ShipmentAlreadyExistsException extends RuntimeException {

    /** Создаёт исключение с сообщением для менеджера. */
    public ShipmentAlreadyExistsException(String message) {
        super(message);
    }
}
