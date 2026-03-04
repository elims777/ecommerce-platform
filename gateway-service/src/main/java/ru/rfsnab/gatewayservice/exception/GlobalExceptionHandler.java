package ru.rfsnab.gatewayservice.exception;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.cloud.gateway.support.NotFoundException;
import org.springframework.cloud.gateway.support.TimeoutException;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.ConnectException;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Глобальный обработчик ошибок для gateway.
 * В реактивном стеке используется ErrorWebExceptionHandler
 * вместо @RestControllerAdvice.
 */
@Slf4j
@Order(-1)
@Component
public class GlobalExceptionHandler implements ErrorWebExceptionHandler {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public @NonNull Mono<Void> handle(@NonNull ServerWebExchange exchange, @NonNull Throwable ex) {
        ServerHttpResponse response = exchange.getResponse();
        String path = exchange.getRequest().getURI().getPath();

        HttpStatus status;
        String message;

        switch (ex) {
            case ConnectException connectException -> {
                // Сервис недоступен (не запущен)
                status = HttpStatus.SERVICE_UNAVAILABLE;
                message = "Сервис временно недоступен";
                log.error("Сервис недоступен для {}: {}", path, ex.getMessage());
            }
            case TimeoutException timeoutException -> {
                // Сервис не ответил вовремя
                status = HttpStatus.GATEWAY_TIMEOUT;
                message = "Сервис не ответил вовремя";
                log.error("Таймаут для {}: {}", path, ex.getMessage());
            }
            case NotFoundException notFoundException -> {
                // Роут не найден
                status = HttpStatus.NOT_FOUND;
                message = "Маршрут не найден";
                log.warn("Маршрут не найден: {}", path);
            }
            case ResponseStatusException rse -> {
                // Ошибка со статусом (429 Too Many Requests и т.д.)
                status = HttpStatus.valueOf(rse.getStatusCode().value());
                message = rse.getReason() != null ? rse.getReason() : status.getReasonPhrase();
                log.warn("Ошибка {} для {}: {}", status.value(), path, message);
            }
            default -> {
                // Всё остальное
                status = HttpStatus.INTERNAL_SERVER_ERROR;
                message = "Внутренняя ошибка сервера";
                log.error("Непредвиденная ошибка для {}: ", path, ex);
            }
        }

        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> errorBody = Map.of(
                "timestamp", LocalDateTime.now().toString(),
                "status", status.value(),
                "error", status.getReasonPhrase(),
                "message", message,
                "path", path
        );

        try {
            byte[] bytes = objectMapper.writeValueAsBytes(errorBody);
            DataBuffer buffer = response.bufferFactory().wrap(bytes);
            return response.writeWith(Mono.just(buffer));
        } catch (JsonProcessingException e) {
            log.error("Ошибка сериализации ответа: ", e);
            return response.setComplete();
        }
    }
}
