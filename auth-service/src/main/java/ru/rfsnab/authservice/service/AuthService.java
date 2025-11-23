package ru.rfsnab.authservice.service;

import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import ru.rfsnab.authservice.models.dto.AuthResponse;
import ru.rfsnab.authservice.models.dto.SimpleAuthRequest;
import ru.rfsnab.authservice.models.dto.UserDtoResponse;
import ru.rfsnab.authservice.utils.JWTService;

import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class AuthService {

    private final RestTemplate restTemplate;
    private final JWTService jwtService;
    @Value("${user.service.url}")
    private String userServiceUrl;
    @Value("${user.service.auth}")
    private String userServiceAuthUrl;

    /**
     * Аутентификация пользователя через user-service
     * Возвращает только токены
     */
    public AuthResponse authenticate(SimpleAuthRequest authRequest){
        try{
            ResponseEntity<UserDtoResponse> response = restTemplate.postForEntity(
                    userServiceUrl + userServiceAuthUrl,
                    authRequest,
                    UserDtoResponse.class
            );

            UserDtoResponse user = response.getBody();
            if(user==null){
                throw new BadCredentialsException("Не удалось получить данные пользователя");
            }

            String accessToken = jwtService.generateToken(authRequest.getEmail());
            String refreshToken = jwtService.generateRefreshToken(authRequest.getEmail());

            log.info("Успешная аутентификация пользователя: {}", user.getEmail());

            return new AuthResponse(accessToken,refreshToken, "Bearer");
        } catch (HttpClientErrorException.Unauthorized | HttpClientErrorException.Forbidden e) {
            log.warn("Неудачная попытка входа: {}", authRequest.getEmail());
            throw new BadCredentialsException("Неверный email или пароль");
        } catch (HttpClientErrorException e) {
            log.error("Ошибка при обращении к user-service: {}", e.getMessage());
            throw new RuntimeException("Ошибка аутентификации. Попробуйте позже");
        }
    }

    /**
     * Обновление access token через refresh token
     */
    public AuthResponse refresh(String refreshToken){
        if(!jwtService.validateToken(refreshToken)){
            throw new JwtException("Недопустимый токен");
        }
        String email = jwtService.extractEmail(refreshToken);

        String accessToken = jwtService.generateToken(email);
        String newRefreshToken = jwtService.generateRefreshToken(email);
        log.info("Refresh token обновлён для пользователя: {}", email);

        return new AuthResponse(accessToken, newRefreshToken,"Bearer");
    }

    /**
     * Аутентификация с возвратом данных пользователя + токенов
     * Используется для redirect на HTML страницу
     */
    public Map<String, Object> authenticateWithUserData(SimpleAuthRequest authRequest) {
        UserDtoResponse user = authenticateUser(authRequest);

        // Генерируем JWT токены
        String accessToken = jwtService.generateToken(user.getEmail());
        String refreshToken = jwtService.generateRefreshToken(user.getEmail());

        log.info("Успешная аутентификация пользователя: {}", user.getEmail());

        // Формируем полный response с данными пользователя
        Map<String, Object> result = new HashMap<>();
        result.put("access_token", accessToken);
        result.put("refresh_token", refreshToken);
        result.put("token_type", "Bearer");
        result.put("expires_in", 3600);
        result.put("user", user);

        return result;
    }

    /**
     * Получение данных текущего пользователя по JWT токену.
     * Вызывает user-service endpoint /v1/users/me с Bearer токеном.
     */
    public UserDtoResponse getCurrentUser(String accessToken){
        String meUrl = userServiceUrl + "/v1/users/me";

        try{
            //Создаем Headers c Bearer токеном
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(accessToken);
            HttpEntity<Void> entity = new HttpEntity<>(headers);

            // GET запрос к user-service
            ResponseEntity<UserDtoResponse> response = restTemplate.exchange(
                    meUrl,
                    HttpMethod.GET,
                    entity,
                    UserDtoResponse.class
            );

            UserDtoResponse user = response.getBody();
            if(user==null){
                throw new RuntimeException("Не удалось получить данные пользователя");
            }
            log.debug("Получены данные пользователя: {}", user.getEmail());
            return user;
        } catch (HttpClientErrorException.Unauthorized e){
            log.warn("Невалидный токен при запросе данных пользователя");
            throw new JwtException("Токен недействителен или истек");
        } catch (HttpClientErrorException e){
            log.error("Ошибка при обращении к user-service: {}", e.getMessage());
            throw new RuntimeException("Ошибка получения данных пользователя");
        }
    }

    /**
     * Внутренний метод для проверки credentials в user-service
     */
    private UserDtoResponse authenticateUser(SimpleAuthRequest authRequest) {
        String authUrl = userServiceUrl + "/v1/users/authenticate";

        try {
            // Отправляем запрос в user-service для проверки credentials
            ResponseEntity<UserDtoResponse> response = restTemplate.postForEntity(
                    authUrl,
                    authRequest,
                    UserDtoResponse.class
            );

            UserDtoResponse user = response.getBody();

            if (user == null) {
                throw new BadCredentialsException("Не удалось получить данные пользователя");
            }

            return user;

        } catch (HttpClientErrorException.Unauthorized | HttpClientErrorException.Forbidden e) {
            log.warn("Неудачная попытка входа: {}", authRequest.getEmail());
            throw new BadCredentialsException("Неверный email или пароль");
        } catch (HttpClientErrorException e) {
            log.error("Ошибка при обращении к user-service: {}", e.getMessage());
            throw new RuntimeException("Ошибка аутентификации. Попробуйте позже");
        }
    }
}
