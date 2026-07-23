package ru.rfsnab.productservice.exception;

public class PriceListPendingException extends RuntimeException {
    public PriceListPendingException(String message) {
        super(message);
    }
}
