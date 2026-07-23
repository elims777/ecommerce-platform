package ru.rfsnab.productservice.exception;

public class PriceListNotReadyException extends RuntimeException {
    public PriceListNotReadyException(String message) {
        super(message);
    }
}
