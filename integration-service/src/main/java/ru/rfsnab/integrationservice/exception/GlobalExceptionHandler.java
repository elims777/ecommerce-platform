package ru.rfsnab.integrationservice.exception;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Глобальный обработчик ошибок для integration-service.
 * Протокол CommerceML ожидает plain text ответы: "failure\n{причина}".
 * Стандартный JSON error response от Spring не подойдёт — 1С его не поймёт.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> handleIllegalArgument(IllegalArgumentException e) {
        return failureResponse("Некорректный запрос: " + e.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<String> handleIllegalState(IllegalStateException e) {
        return failureResponse("Ошибка состояния: " + e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<String> handleGeneral(Exception e) {
        return failureResponse("Внутренняя ошибка сервера: " + e.getMessage());
    }

    private ResponseEntity<String> failureResponse(String message) {
        return ResponseEntity.internalServerError()
                .contentType(MediaType.TEXT_PLAIN)
                .body("failure\n" + message);
    }
}