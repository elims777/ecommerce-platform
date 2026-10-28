package ru.rfsnab.userservice.exceptions;

/**
 * Выбрасывается, когда ссылка отписки от рассылки недействительна.
 */
public class InvalidUnsubscribeTokenException extends RuntimeException {
    public InvalidUnsubscribeTokenException(String message) {
        super(message);
    }
}
