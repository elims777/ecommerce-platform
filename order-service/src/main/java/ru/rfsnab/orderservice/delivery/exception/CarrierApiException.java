package ru.rfsnab.orderservice.delivery.exception;

/**
 * Ответ ТК не удалось получить или разобрать. Сообщение безопасно для логов и отчёта: без URL и ключей.
 */
public class CarrierApiException extends RuntimeException {

    /** Создаёт исключение с безопасным сообщением. */
    public CarrierApiException(String message) {
        super(message);
    }
}
