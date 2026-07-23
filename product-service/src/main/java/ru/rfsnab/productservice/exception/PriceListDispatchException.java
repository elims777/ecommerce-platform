package ru.rfsnab.productservice.exception;

/**
 * Сбой при отправке события генерации прайс-листа в Kafka (POST → 503).
 */
public class PriceListDispatchException extends RuntimeException {
    public PriceListDispatchException(String message, Throwable cause) {
        super(message, cause);
    }
}
