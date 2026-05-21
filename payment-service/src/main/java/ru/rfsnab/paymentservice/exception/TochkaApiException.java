package ru.rfsnab.paymentservice.exception;

import org.springframework.http.HttpStatus;

public class TochkaApiException extends RuntimeException {
    private final HttpStatus httpStatus;

    public TochkaApiException(String message, HttpStatus httpStatus) {
        super(message);
        this.httpStatus = httpStatus;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }
}
