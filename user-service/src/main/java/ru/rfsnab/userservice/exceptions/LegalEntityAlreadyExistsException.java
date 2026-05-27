package ru.rfsnab.userservice.exceptions;

/**
 * Выбрасывается при попытке зарегистрировать юридическое лицо с уже существующим ИНН или email.
 */
public class LegalEntityAlreadyExistsException extends RuntimeException {
    public LegalEntityAlreadyExistsException(String message) {
        super(message);
    }
}
