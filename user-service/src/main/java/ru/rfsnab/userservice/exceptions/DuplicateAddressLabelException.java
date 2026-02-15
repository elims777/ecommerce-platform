package ru.rfsnab.userservice.exceptions;

public class DuplicateAddressLabelException extends RuntimeException{
    public DuplicateAddressLabelException(String message){
        super(message);
    }
}
