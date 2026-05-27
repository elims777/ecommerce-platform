package ru.rfsnab.userservice.exceptions;

/**
 * Выбрасывается при попытке использовать юридическое лицо, не прошедшее верификацию менеджером.
 */
public class LegalEntityNotVerifiedException extends RuntimeException {
    public LegalEntityNotVerifiedException(String message) {
        super(message);
    }
}
