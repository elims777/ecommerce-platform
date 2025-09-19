package ru.rfsnab.userservice.exceptions;

import lombok.Getter;
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;
@Getter
public class CustomException extends RuntimeException{
    private final int status;
    private final String message;
    private final LocalDateTime time;

    public CustomException(String message, int status) {
        super(message);
        this.status = status;
        this.message = message;
        time = LocalDateTime.now();
    }

    public CustomException(String message) {
        super(message);
        this.status= HttpStatus.INTERNAL_SERVER_ERROR.value();
        this.message = message;
        time = LocalDateTime.now();
    }
}
