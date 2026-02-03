package ru.rfsnab.orderservice.exception;

/**
 * Исключение: товар не найден в product-service.
 */
public class ProductNotFoundException extends RuntimeException {
    public ProductNotFoundException(String message){
        super(message);
    }
}
