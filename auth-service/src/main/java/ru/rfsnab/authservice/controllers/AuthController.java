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
import ru.rfsnab.authservice.models.dto.SimpleAuthRequest;
import ru.rfsnab.authservice.models.dto.UserDtoResponse;
import ru.rfsnab.authservice.service.AuthService;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
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
    public ResponseEntity<?> getCurrentUser(@RequestHeader("Authorization") String authHeader){
        try{
            //Создаем header c authorization
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", authHeader);
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Void> httpEntity = new HttpEntity<>(headers);

            // Запрос в user-service
            ResponseEntity<UserDtoResponse> response = restTemplate.exchange(
                    userServiceUrl + "/v1/users/me",
                    HttpMethod.GET,
                    httpEntity,
                    UserDtoResponse.class
            );

            return ResponseEntity.ok(response.getBody());
        } catch (HttpClientErrorException.Unauthorized e) {
            log.warn("Unauthorized request to /me");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Недействительный или истёкший токен"));
        } catch (HttpClientErrorException.Forbidden e) {
            log.warn("Forbidden request to /me");
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Доступ запрещён"));
        } catch (Exception e) {
            log.error("Error getting user data: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Ошибка получения данных пользователя"));
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
