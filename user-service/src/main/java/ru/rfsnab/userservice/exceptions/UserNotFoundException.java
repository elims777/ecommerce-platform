package ru.rfsnab.userservice.exceptions;

/**
 * Выбрасывается когда пользователь не найден по заданному критерию.
 */
public class UserNotFoundException extends RuntimeException {
    public UserNotFoundException(String message) {
        super(message);
    }
}
