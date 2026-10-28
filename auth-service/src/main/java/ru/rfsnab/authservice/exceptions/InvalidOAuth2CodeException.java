package ru.rfsnab.authservice.exceptions;

public class InvalidOAuth2CodeException extends RuntimeException {

    public InvalidOAuth2CodeException(String message) {
        super(message);
    }
}
