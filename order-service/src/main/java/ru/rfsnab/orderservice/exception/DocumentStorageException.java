package ru.rfsnab.orderservice.exception;

/**
 * Исключение: ошибка при работе с объектным хранилищем (Yandex S3)
 * во время загрузки/скачивания/удаления документа заказа.
 */
public class DocumentStorageException extends RuntimeException {
    public DocumentStorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
