package ru.rfsnab.orderservice.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Стандартный формат ответа об ошибке.
 * Используется в GlobalExceptionHandler и SecurityConfig.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ErrorResponse {
    private LocalDateTime timestamp;
    private int status;
    private String error;
    private String message;
    private String path;
    /** Машиночитаемый код ошибки; null, если не задан. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String code;
}
