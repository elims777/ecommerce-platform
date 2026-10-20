package ru.rfsnab.orderservice.delivery.exception;

/** Отправление не найдено в нашей БД. */
public class ShipmentNotFoundException extends RuntimeException {

    /** Создаёт исключение с сообщением для менеджера. */
    public ShipmentNotFoundException(String message) {
        super(message);
    }
}
