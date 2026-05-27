package ru.rfsnab.userservice.exceptions;

/**
 * Выбрасывается когда юридическое лицо не найдено по заданному критерию.
 */
public class LegalEntityNotFoundException extends RuntimeException {
    public LegalEntityNotFoundException(String message) {
        super(message);
    }
}
