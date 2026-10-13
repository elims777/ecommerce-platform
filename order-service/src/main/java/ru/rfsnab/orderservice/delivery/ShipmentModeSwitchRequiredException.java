package ru.rfsnab.orderservice.delivery;

/**
 * Вторая накладная в режиме SINGLE: фронт спрашивает «Переключить на несколько отправлений?»
 * и повторяет запрос с shipmentMode=MULTIPLE.
 */
public class ShipmentModeSwitchRequiredException extends RuntimeException {

    /** Код ошибки в теле ответа 409. */
    public static final String CODE = "SHIPMENT_MODE_SWITCH_REQUIRED";

    /** Создаёт исключение с кодом {@link #CODE} в сообщении. */
    public ShipmentModeSwitchRequiredException() {
        super(CODE + ": у заказа режим «Одним отправлением» — для второй накладной переключите на несколько отправлений");
    }
}
