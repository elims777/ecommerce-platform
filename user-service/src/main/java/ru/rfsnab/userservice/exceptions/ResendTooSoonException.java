package ru.rfsnab.userservice.exceptions;

public class ResendTooSoonException extends RuntimeException {
    public ResendTooSoonException(String message) {
        super(message);
    }
}
