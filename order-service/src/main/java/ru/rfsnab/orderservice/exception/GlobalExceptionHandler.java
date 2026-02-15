package ru.rfsnab.orderservice.exception;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.stream.Collectors;

/**
 * Глобальный обработчик исключений для order-service.
 * Перехватывает все исключения из контроллеров и возвращает
 * стандартный ErrorResponse с правильным HTTP статусом.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {
    // ==================== 400 Bad Request ====================

    /**
     * Пустая корзина при создании заказа.
     */
    @ExceptionHandler(CartEmptyException.class)
    public ResponseEntity<ErrorResponse> handleCartEmpty(
            CartEmptyException ex, HttpServletRequest request) {
        return buildResponse(HttpStatus.BAD_REQUEST, "Cart Empty", ex.getMessage(), request);
    }

    /**
     * Недопустимая операция с заказом (неправильный статус, нет доступа).
     */
    @ExceptionHandler(InvalidOrderStateException.class)
    public ResponseEntity<ErrorResponse> handleInvalidOrderState(
            InvalidOrderStateException ex, HttpServletRequest request) {
        return buildResponse(HttpStatus.BAD_REQUEST, "Invalid Order State", ex.getMessage(), request);
    }

    /**
     * Bean Validation — @NotNull, @NotEmpty, @Valid и т.д.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(
            MethodArgumentNotValidException ex, HttpServletRequest request) {

        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining("; "));

        return buildResponse(HttpStatus.BAD_REQUEST, "Validation Failed", message, request);
    }

    // ==================== 404 Not Found ====================

    /**
     * Заказ не найден.
     */
    @ExceptionHandler(OrderNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleOrderNotFound(
            OrderNotFoundException ex, HttpServletRequest request) {
        return buildResponse(HttpStatus.NOT_FOUND, "Order Not Found", ex.getMessage(), request);
    }

    /**
     * Товар не найден в product-service.
     */
    @ExceptionHandler(ProductNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleProductNotFound(
            ProductNotFoundException ex, HttpServletRequest request) {
        return buildResponse(HttpStatus.NOT_FOUND, "Product Not Found", ex.getMessage(), request);
    }

    /**
     * Точка самовывоза не найдена.
     */
    @ExceptionHandler(WarehousePointNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleWarehousePointNotFound(
            WarehousePointNotFoundException ex, HttpServletRequest request) {
        return buildResponse(HttpStatus.NOT_FOUND, "Warehouse Point Not Found", ex.getMessage(), request);
    }

    // ==================== 409 Conflict ====================

    /**
     * Недостаточно товара на складе.
     */
    @ExceptionHandler(InsufficientStockException.class)
    public ResponseEntity<ErrorResponse> handleInsufficientStock(
            InsufficientStockException ex, HttpServletRequest request) {
        return buildResponse(HttpStatus.CONFLICT, "Insufficient Stock", ex.getMessage(), request);
    }

    // ==================== 503 Service Unavailable ====================

    /**
     * Внешний сервис недоступен (product-service, payment-service).
     */
    @ExceptionHandler(ServiceUnavailableException.class)
    public ResponseEntity<ErrorResponse> handleServiceUnavailable(
            ServiceUnavailableException ex, HttpServletRequest request) {
        return buildResponse(HttpStatus.SERVICE_UNAVAILABLE, "Service Unavailable", ex.getMessage(), request);
    }

    // ==================== 500 Internal Server Error ====================

    /**
     * Все необработанные исключения.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneral(
            Exception ex, HttpServletRequest request) {
        log.error("Необработанная ошибка на {}: {}", request.getRequestURI(), ex.getMessage(), ex);
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error",
                "Внутренняя ошибка сервера", request);
    }

    // ==================== Helper ====================

    private ResponseEntity<ErrorResponse> buildResponse(
            HttpStatus status, String error, String message, HttpServletRequest request) {

        ErrorResponse response = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(status.value())
                .error(error)
                .message(message)
                .path(request.getRequestURI())
                .build();

        return ResponseEntity.status(status).body(response);
    }
}
