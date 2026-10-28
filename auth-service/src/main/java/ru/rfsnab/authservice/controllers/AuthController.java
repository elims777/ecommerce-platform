package ru.rfsnab.authservice.controllers;

import io.jsonwebtoken.JwtException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import ru.rfsnab.authservice.models.dto.AuthResponse;
import ru.rfsnab.authservice.models.dto.LegalAuthRequest;
import ru.rfsnab.authservice.models.dto.OAuth2ExchangeRequest;
import ru.rfsnab.authservice.models.dto.RefreshTokenRequest;
import ru.rfsnab.authservice.models.dto.SimpleAuthRequest;
import ru.rfsnab.authservice.models.dto.SwitchContextRequest;
import ru.rfsnab.authservice.models.dto.UserDtoResponse;
import ru.rfsnab.authservice.service.AuthService;
import ru.rfsnab.authservice.service.OAuth2CodeService;

import java.util.Map;

@RestController
@Slf4j
@RequestMapping("/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private static final String BEARER_PREFIX = "Bearer ";

    private final AuthService authService;
    private final OAuth2CodeService oAuth2CodeService;

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
     * Обновление access token через refresh token
     */
    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return ResponseEntity.ok(authService.refresh(request.refreshToken()));
    }

    /**
     * Получение данных текущего пользователя.
     * Проксирует запрос в user-service с JWT токеном.
     */
    @GetMapping("me")
    public ResponseEntity<UserDtoResponse> getCurrentUser(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader) {
        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            throw new JwtException("Отсутствует или некорректен заголовок Authorization");
        }
        return ResponseEntity.ok(authService.getCurrentUser(authHeader.substring(BEARER_PREFIX.length())));
    }

    @PostMapping("/login/legal")
    public ResponseEntity<AuthResponse> loginLegal(@RequestBody LegalAuthRequest request) {
        return ResponseEntity.ok(authService.authenticateLegalEntity(request));
    }

    /**
     * Обмен одноразового кода OAuth2 на токены (код выдаётся в редиректе OAuth2LoginSuccessHandler)
     */
    @PostMapping("/oauth2/exchange")
    public ResponseEntity<Map<String, Object>> exchangeOAuth2Code(@Valid @RequestBody OAuth2ExchangeRequest request) {
        return ResponseEntity.ok(oAuth2CodeService.exchange(request.code()));
    }

    @PostMapping("/switch-context")
    public ResponseEntity<AuthResponse> switchContext(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader,
            @RequestBody SwitchContextRequest request) {
        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            throw new JwtException("Отсутствует или некорректен заголовок Authorization");
        }
        String token = authHeader.substring(BEARER_PREFIX.length());
        return ResponseEntity.ok(authService.switchContext(token, request));
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
