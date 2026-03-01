package ru.rfsnab.authservice.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import ru.rfsnab.authservice.models.dto.AuthResponse;
import ru.rfsnab.authservice.models.dto.ErrorResponse;
import ru.rfsnab.authservice.models.dto.SimpleAuthRequest;
import ru.rfsnab.authservice.models.dto.UserDtoResponse;
import ru.rfsnab.authservice.service.AuthService;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Map;

@RestController
@Slf4j
@RequestMapping("/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;

    @Value("${user.service.url}")
    private String userServiceUrl;

    /**
     * REST API endpoint для login (возвращает JSON)
     * Используется для мобильных приложений, Postman, etc.
     */
    @PostMapping(value = "/login", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> loginJson(@Valid @RequestBody SimpleAuthRequest authRequest) {
        Map<String, Object> authData = authService.authenticateWithUserData(authRequest);
        return ResponseEntity.ok(authData);
    }

    /**
     * HTML form login endpoint (делает redirect на страницу успеха)
     * Используется когда login идёт через HTML форму в браузере
     * Проверяем Accept header: если там text/html - делаем redirect
     */
    @PostMapping(value = "/login",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.TEXT_HTML_VALUE)
    public void loginHtml(@Valid @RequestBody SimpleAuthRequest authRequest,
                          HttpServletResponse response) throws IOException {

        Map<String, Object> authData = authService.authenticateWithUserData(authRequest);

        // Формируем URL с данными для страницы успеха
        String dataJson = objectMapper.writeValueAsString(authData);
        String encodedData = URLEncoder.encode(dataJson, StandardCharsets.UTF_8);

        response.sendRedirect("/oauth2/success?data=" + encodedData);
    }

    /**
     * Обновление access token через refresh token
     */
    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(@RequestParam String refreshToken) {
        return ResponseEntity.ok(authService.refresh(refreshToken));
    }

    /**
     * Получение данных текущего пользователя.
     * Проксирует запрос в user-service с JWT токеном.
     */
    @GetMapping("me")
    public ResponseEntity<?> getCurrentUser(@RequestHeader(value = "Authorization", required = false) String authHeader){
        try {
            // Проверка наличия заголовка
            if (authHeader == null || authHeader.isBlank()) {
                log.warn("Missing Authorization header in /me request");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(ErrorResponse.builder()
                                .timestamp(LocalDateTime.now())
                                .status(HttpStatus.UNAUTHORIZED.value())
                                .error("Unauthorized")
                                .message("Требуется аутентификация. Токен не предоставлен")
                                .path("/v1/auth/me")
                                .build());
            }

            // Проверка формата Bearer
            if (!authHeader.startsWith("Bearer ")) {
                log.warn("Invalid Authorization header format in /me request");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(ErrorResponse.builder()
                                .timestamp(LocalDateTime.now())
                                .status(HttpStatus.UNAUTHORIZED.value())
                                .error("Unauthorized")
                                .message("Неверный формат токена. Ожидается: Bearer <token>")
                                .path("/v1/auth/me")
                                .build());
            }

            // Подготовка заголовков для user-service
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", authHeader);
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Void> requestEntity = new HttpEntity<>(headers);

            // Запрос к user-service
            ResponseEntity<UserDtoResponse> response = restTemplate.exchange(
                    userServiceUrl + "/v1/users/me",
                    HttpMethod.GET,
                    requestEntity,
                    UserDtoResponse.class
            );

            return ResponseEntity.ok(response.getBody());

        } catch (HttpClientErrorException.Unauthorized e) {
            log.warn("Unauthorized request to /me: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ErrorResponse.builder()
                            .timestamp(LocalDateTime.now())
                            .status(HttpStatus.UNAUTHORIZED.value())
                            .error("Unauthorized")
                            .message("Токен недействителен или истёк. Пожалуйста, войдите снова")
                            .path("/v1/auth/me")
                            .build());

        } catch (HttpClientErrorException.Forbidden e) {
            log.warn("Forbidden request to /me: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ErrorResponse.builder()
                            .timestamp(LocalDateTime.now())
                            .status(HttpStatus.FORBIDDEN.value())
                            .error("Forbidden")
                            .message("Доступ запрещён")
                            .path("/v1/auth/me")
                            .build());

        } catch (HttpClientErrorException.NotFound e) {
            log.warn("User not found in /me request");
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ErrorResponse.builder()
                            .timestamp(LocalDateTime.now())
                            .status(HttpStatus.NOT_FOUND.value())
                            .error("Not Found")
                            .message("Пользователь не найден")
                            .path("/v1/auth/me")
                            .build());

        } catch (HttpClientErrorException e) {
            log.error("Client error in /me request: {}", e.getMessage());
            return ResponseEntity.status(e.getStatusCode())
                    .body(ErrorResponse.builder()
                            .timestamp(LocalDateTime.now())
                            .status(e.getStatusCode().value())
                            .error(e.getStatusText())
                            .message("Ошибка при получении данных пользователя")
                            .path("/v1/auth/me")
                            .build());

        } catch (Exception e) {
            log.error("Unexpected error in /me request", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ErrorResponse.builder()
                            .timestamp(LocalDateTime.now())
                            .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                            .error("Internal Server Error")
                            .message("Внутренняя ошибка сервера")
                            .path("/v1/auth/me")
                            .build());
        }
    }


    /**
     * Logout endpoint (пока просто возвращает success)
     */
    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout() {
        return ResponseEntity.ok(Map.of(
                "message", "Logout successful",
                "status", "success"
        ));
    }
}
