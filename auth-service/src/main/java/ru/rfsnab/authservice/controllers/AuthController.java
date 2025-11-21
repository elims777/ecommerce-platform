package ru.rfsnab.authservice.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.rfsnab.authservice.models.dto.AuthResponse;
import ru.rfsnab.authservice.models.dto.SimpleAuthRequest;
import ru.rfsnab.authservice.service.AuthService;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@RestController
@RequestMapping("/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final ObjectMapper objectMapper;

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
