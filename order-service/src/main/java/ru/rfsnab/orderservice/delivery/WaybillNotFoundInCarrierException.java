package ru.rfsnab.orderservice.delivery;

/** Вносимой вручную накладной нет в ТК — сохранять её нельзя. */
public class WaybillNotFoundInCarrierException extends RuntimeException {

    /** Создаёт исключение с сообщением для менеджера. */
    public WaybillNotFoundInCarrierException(String message) {
        super(message);
    }
}
