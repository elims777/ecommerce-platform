package ru.rfsnab.productservice.exception;

public class PriceListNotFoundException extends RuntimeException {
    public PriceListNotFoundException(Long id) {
        super("Прайс-лист с id=" + id + " не найден");
    }
}
