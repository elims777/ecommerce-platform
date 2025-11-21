package ru.rfsnab.userservice.exceptions;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;


public class CustomExceptionHandler {

    @ExceptionHandler(CustomException.class)
    public ResponseEntity<ErrorResponse> exceptionHandler(CustomException ex) {
        return ResponseEntity
                .status(ex.getStatus())  // ← Использовать статус из exception
                .body(new ErrorResponse(ex.getMessage()));
    }
}
