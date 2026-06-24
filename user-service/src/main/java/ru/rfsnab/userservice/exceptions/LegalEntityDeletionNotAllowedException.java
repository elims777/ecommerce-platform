package ru.rfsnab.userservice.exceptions;

public class LegalEntityDeletionNotAllowedException extends RuntimeException {
    public LegalEntityDeletionNotAllowedException(String message) {
        super(message);
    }
}
