package ru.rfsnab.orderservice.exception;

/**
 * Исключение: внешний сервис недоступен.
 */
public class ServiceUnavailableException extends RuntimeException {
    public ServiceUnavailableException (String message){
        super(message);
    }
}
