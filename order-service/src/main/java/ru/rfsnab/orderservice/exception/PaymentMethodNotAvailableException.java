package ru.rfsnab.orderservice.exception;

public class PaymentMethodNotAvailableException extends BusinessException {
    public PaymentMethodNotAvailableException(String message) {
        super(message);
    }
}
