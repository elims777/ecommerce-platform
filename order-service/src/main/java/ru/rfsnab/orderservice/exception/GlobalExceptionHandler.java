package ru.rfsnab.orderservice.exception;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mapping.PropertyReferenceException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import ru.rfsnab.orderservice.delivery.exception.CarrierApiException;
import ru.rfsnab.orderservice.delivery.exception.ShipmentAlreadyExistsException;
import ru.rfsnab.orderservice.delivery.exception.ShipmentModeSwitchRequiredException;
import ru.rfsnab.orderservice.delivery.exception.ShipmentNotFoundException;
import ru.rfsnab.orderservice.delivery.exception.WaybillNotFoundInCarrierException;

import java.time.LocalDateTime;
import java.util.Map;
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

    /**
     * Нечитаемое тело запроса — битый JSON, лишнее или неверного типа поле.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleNotReadable(
            HttpMessageNotReadableException ex, HttpServletRequest request) {
        log.warn("Некорректное тело запроса на {}", request.getRequestURI());
        return buildResponse(HttpStatus.BAD_REQUEST, "Malformed Request", "Некорректное тело запроса", request);
    }

    /**
     * Параметр пути/запроса не приводится к нужному типу (неизвестный код ТК, нечисловой id).
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        log.warn("Некорректный параметр на {}", request.getRequestURI());
        return buildResponse(HttpStatus.BAD_REQUEST, "Invalid Parameter",
                "Некорректное значение параметра " + ex.getName(), request);
    }

    /**
     * Неизвестное поле в sort (например, журнал отправлений).
     */
    @ExceptionHandler(PropertyReferenceException.class)
    public ResponseEntity<ErrorResponse> handleInvalidSortProperty(
            PropertyReferenceException ex, HttpServletRequest request) {
        return buildResponse(HttpStatus.BAD_REQUEST, "Invalid Sort",
                "Некорректное поле сортировки: " + ex.getPropertyName(), request);
    }

    /**
     * Не передан обязательный параметр запроса (например, carrier/city/type справочника терминалов).
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParameter(
            MissingServletRequestParameterException ex, HttpServletRequest request) {
        return buildResponse(HttpStatus.BAD_REQUEST, "Missing Parameter",
                "Не передан параметр " + ex.getParameterName(), request);
    }

    /**
     * Не передан обязательный заголовок запроса.
     */
    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ErrorResponse> handleMissingHeader(
            MissingRequestHeaderException ex, HttpServletRequest request) {
        return buildResponse(HttpStatus.BAD_REQUEST, "Missing Header",
                "Не передан заголовок " + ex.getHeaderName(), request);
    }

    /**
     * Не передана обязательная часть multipart-запроса.
     */
    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<ErrorResponse> handleMissingPart(
            MissingServletRequestPartException ex, HttpServletRequest request) {
        return buildResponse(HttpStatus.BAD_REQUEST, "Missing Part",
                "Не передана часть запроса " + ex.getRequestPartName(), request);
    }

    /**
     * Невалидный файл документа заказа (тип, размер, пустой файл).
     */
    @ExceptionHandler(InvalidDocumentException.class)
    public ResponseEntity<ErrorResponse> handleInvalidDocument(
            InvalidDocumentException ex, HttpServletRequest request) {
        return buildResponse(HttpStatus.BAD_REQUEST, "Invalid Document", ex.getMessage(), request);
    }

    /**
     * Вносимой вручную накладной нет в ТК.
     */
    @ExceptionHandler(WaybillNotFoundInCarrierException.class)
    public ResponseEntity<ErrorResponse> handleWaybillNotFoundInCarrier(
            WaybillNotFoundInCarrierException ex, HttpServletRequest request) {
        return buildResponse(HttpStatus.BAD_REQUEST, "Waybill Not Found In Carrier", ex.getMessage(), request);
    }

    // ==================== 405 / 415 ====================

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotSupported(
            HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {
        return buildResponse(HttpStatus.METHOD_NOT_ALLOWED, "Method Not Allowed",
                "Метод " + ex.getMethod() + " не поддерживается", request);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMediaTypeNotSupported(
            HttpMediaTypeNotSupportedException ex, HttpServletRequest request) {
        return buildResponse(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Unsupported Media Type",
                "Неподдерживаемый тип содержимого", request);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleMaxUploadSize(
            MaxUploadSizeExceededException ex, HttpServletRequest request) {
        return buildResponse(HttpStatus.PAYLOAD_TOO_LARGE, "Payload Too Large",
                "Файл слишком большой", request);
    }

    // ==================== 404 Not Found ====================

    /**
     * Несуществующий путь.
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResourceFound(
            NoResourceFoundException ex, HttpServletRequest request) {
        log.warn("Ресурс не найден: {}", request.getRequestURI());
        return buildResponse(HttpStatus.NOT_FOUND, "Not Found", "Ресурс не найден", request);
    }

    /**
     * Заказ не найден.
     */
    @ExceptionHandler(OrderNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleOrderNotFound(
            OrderNotFoundException ex, HttpServletRequest request) {
        return buildResponse(HttpStatus.NOT_FOUND, "Order Not Found", ex.getMessage(), request);
    }

    /**
     * Получатель или адрес получателя не найден (в том числе чужой).
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusiness(
            BusinessException ex, HttpServletRequest request) {
        return buildResponse(HttpStatus.NOT_FOUND, "Not Found", ex.getMessage(), request);
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

    /**
     * Точка самовывоза с таким названием уже есть.
     */
    @ExceptionHandler(DuplicateWarehousePointException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateWarehousePoint(
            DuplicateWarehousePointException ex, HttpServletRequest request) {
        return buildResponse(HttpStatus.CONFLICT, "Warehouse Point Name Taken", ex.getMessage(), request);
    }

    /**
     * Документ заказа не найден.
     */
    @ExceptionHandler(OrderDocumentNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleOrderDocumentNotFound(
            OrderDocumentNotFoundException ex, HttpServletRequest request) {
        return buildResponse(HttpStatus.NOT_FOUND, "Order Document Not Found", ex.getMessage(), request);
    }

    /**
     * Отправление не найдено.
     */
    @ExceptionHandler(ShipmentNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleShipmentNotFound(
            ShipmentNotFoundException ex, HttpServletRequest request) {
        return buildResponse(HttpStatus.NOT_FOUND, "Shipment Not Found", ex.getMessage(), request);
    }

    /**
     * Накладная этой ТК уже внесена.
     */
    @ExceptionHandler(ShipmentAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handleShipmentAlreadyExists(
            ShipmentAlreadyExistsException ex, HttpServletRequest request) {
        return buildResponse(HttpStatus.CONFLICT, "Shipment Already Exists", ex.getMessage(), request);
    }

    /**
     * Вторая накладная при режиме SINGLE: код SHIPMENT_MODE_SWITCH_REQUIRED в error, message и code.
     */
    @ExceptionHandler(ShipmentModeSwitchRequiredException.class)
    public ResponseEntity<ErrorResponse> handleShipmentModeSwitchRequired(
            ShipmentModeSwitchRequiredException ex, HttpServletRequest request) {
        ErrorResponse response = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.CONFLICT.value())
                .error(ShipmentModeSwitchRequiredException.CODE)
                .code(ShipmentModeSwitchRequiredException.CODE)
                .message(ex.getMessage())
                .path(request.getRequestURI())
                .build();
        return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
    }

    // ==================== 502 Bad Gateway ====================

    /**
     * ТК недоступна или ответила непонятно (ручное обновление статуса).
     */
    @ExceptionHandler(CarrierApiException.class)
    public ResponseEntity<ErrorResponse> handleCarrierApi(
            CarrierApiException ex, HttpServletRequest request) {
        log.warn("Ошибка ТК на {}: {}", request.getRequestURI(), ex.getMessage());
        return buildResponse(HttpStatus.BAD_GATEWAY, "Carrier Error", ex.getMessage(), request);
    }

    // ==================== 422 Unprocessable Entity ====================

    /**
     * Профиль пользователя не заполнен полностью — блокирует корзину и заказ.
     */
    @ExceptionHandler(ProfileIncompleteException.class)
    public ResponseEntity<Map<String, Object>> handleProfileIncomplete(ProfileIncompleteException ex) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(Map.of("message", ex.getMessage(), "missing", ex.getMissing()));
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

    @ExceptionHandler(PaymentMethodNotAvailableException.class)
    public ResponseEntity<ErrorResponse> handlePaymentMethodNotAvailable(
            PaymentMethodNotAvailableException ex, HttpServletRequest request) {
        return buildResponse(HttpStatus.BAD_REQUEST, "Payment Method Not Available", ex.getMessage(), request);
    }

    // ==================== 403 Forbidden ====================

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(
            AccessDeniedException ex, HttpServletRequest request) {
        return buildResponse(HttpStatus.FORBIDDEN, "Forbidden", "Недостаточно прав для выполнения операции", request);
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
