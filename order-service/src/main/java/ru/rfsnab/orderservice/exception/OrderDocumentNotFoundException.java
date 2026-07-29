package ru.rfsnab.orderservice.exception;

/**
 * Исключение: документ заказа не найден (или не принадлежит указанному заказу).
 */
public class OrderDocumentNotFoundException extends RuntimeException {
    public OrderDocumentNotFoundException(String message) {
        super(message);
    }
}
