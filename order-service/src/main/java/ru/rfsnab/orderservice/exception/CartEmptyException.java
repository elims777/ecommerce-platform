package ru.rfsnab.orderservice.exception;

public class CartEmptyException extends RuntimeException{
    public CartEmptyException(String message){
        super(message);
    }
}
