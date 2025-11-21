package ru.rfsnab.authservice.controllers;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
import ru.rfsnab.authservice.models.dto.UserDtoResponse;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class RegistrationController {

    private final RestTemplate restTemplate;
    private final PasswordEncoder passwordEncoder;

    // base URL user-service, задаём в application.yml (см. ниже)
    @Value("${user.service.url}")
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
            UserDtoResponse created =
                    restTemplate.postForObject(
                            userServiceUrl + "/v1/users/signup",
                                request,
                                UserDtoResponse.class
                    );
            if(created==null){
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(Map.of("error", "Failed to create user: empty response"));
            }
            return ResponseEntity.status(HttpStatus.CREATED).body(created);
        } catch (HttpClientErrorException.Conflict e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new ErrorResponse("User already exists"));
        } catch (HttpClientErrorException.BadRequest e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ErrorResponse("Invalid registration data"));
        } catch (Exception e) {
            log.error("Registration failed", e); // добавь логгер!
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Registration failed due to internal error"));
        }
    }

    record ErrorResponse(String error) { }
}