package ru.rfsnab.orderservice.exception;

/**
 * Исключение: файл документа заказа не проходит валидацию
 * (пустой, недопустимый тип, превышен размер).
 */
public class InvalidDocumentException extends RuntimeException {
    public InvalidDocumentException(String message) {
        super(message);
    }
}
