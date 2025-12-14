package ru.rfsnab.productservice.exception;

public class ProductNotFoundException extends RuntimeException{
    public ProductNotFoundException(Long id) {
        super("Товар с id=" + id + " не найден");
    }

    public ProductNotFoundException(String slug) {
        super("Товар с slug='" + slug + "' не найден");
    }

    public ProductNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
