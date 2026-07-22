package ru.rfsnab.productservice.exception;

/**
 * 400 Bad Request для несуществующей категории при создании прайс-листа
 * (в отличие от CategoryNotFoundException, которая маппится в 404).
 */
public class CategoryValidationException extends RuntimeException {
    public CategoryValidationException(String message) {
        super(message);
    }
}
