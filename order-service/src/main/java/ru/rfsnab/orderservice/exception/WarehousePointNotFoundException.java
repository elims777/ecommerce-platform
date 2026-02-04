package ru.rfsnab.orderservice.exception;

public class WarehousePointNotFoundException extends RuntimeException {
    public WarehousePointNotFoundException(String message){
        super(message);
    }
}
