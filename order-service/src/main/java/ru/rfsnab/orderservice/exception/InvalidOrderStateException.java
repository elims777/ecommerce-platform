package ru.rfsnab.orderservice.exception;
/**
 * Исключение: недопустимая операция с заказом.
 * Невалидный переход статуса или нет прав доступа.
 */
public class InvalidOrderStateException extends RuntimeException{
    public InvalidOrderStateException(String message) {
        super(message);
    }
}
