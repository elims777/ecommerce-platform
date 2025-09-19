package ru.rfsnab.userservice.util;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import ru.rfsnab.userservice.exceptions.CustomException;

@ControllerAdvice
public class CustomExceptionHandler {

    @ExceptionHandler(value = {CustomException.class})
    protected ResponseEntity<CustomException> exceptionHandler(Exception e){
        return ResponseEntity.badRequest().body(new CustomException(e.getMessage()));
    }
}
