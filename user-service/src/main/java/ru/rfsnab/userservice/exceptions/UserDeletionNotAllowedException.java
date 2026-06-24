package ru.rfsnab.userservice.exceptions;

public class UserDeletionNotAllowedException extends RuntimeException {
    public UserDeletionNotAllowedException(String message) {
        super(message);
    }
}
