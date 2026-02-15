package ru.rfsnab.orderservice.exception;

/**
 * Исключение: недостаточно товара на складе.
 */
public class InsufficientStockException extends RuntimeException {
    public InsufficientStockException (String message){
        super(message);
    }
}
