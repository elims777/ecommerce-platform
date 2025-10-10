package ru.rfsnab.authservice.controllers;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import ru.rfsnab.authservice.models.dto.RegistrationRequest;
import ru.rfsnab.authservice.models.dto.UserDto;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class RegistrationController {

    private final RestTemplate restTemplate;
    private final PasswordEncoder passwordEncoder;

    // base URL user-service, задаём в application.yml (см. ниже)
    @Value("${user.service.url:http://localhost:8081}")
    private String userServiceUrl;

    /**
     * Регистрация через JSON: прокидываем запрос в user-service (POST /api/users).
     * Ожидаем, что user-service сам валидирует, хеширует пароль и вернёт созданного пользователя.
     */
    @PostMapping(value = "/register",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> registerJson(@Valid @RequestBody RegistrationRequest request) {
        request.setPassword(passwordEncoder.encode(request.getPassword()));
        try {
            UserDto created =
                    restTemplate.postForObject(
                            userServiceUrl + "/v1/users/signup",
                                request,
                                UserDto.class
                    );
            return ResponseEntity.status(HttpStatus.CREATED).body(created);
        } catch (HttpClientErrorException.Conflict e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "User already exists"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Registration failed"));
        }
    }
}