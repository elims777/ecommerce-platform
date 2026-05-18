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
import ru.rfsnab.authservice.models.dto.LegalAuthRequest;
import ru.rfsnab.authservice.models.dto.LegalEntityAuthResponse;
import ru.rfsnab.authservice.models.dto.LegalEntityDto;
import ru.rfsnab.authservice.models.dto.RoleEntity;
import ru.rfsnab.authservice.models.dto.SimpleAuthRequest;
import ru.rfsnab.authservice.models.dto.SwitchContextRequest;
import ru.rfsnab.authservice.models.dto.UserDtoResponse;
import ru.rfsnab.authservice.utils.JWTService;
import ru.rfsnab.authservice.utils.RoleExtractor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class AuthService {

    private final RestTemplate restTemplate;
    private final JWTService jwtService;
    private final RoleExtractor roleExtractor;

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

            List<String> roles = roleExtractor.extractRoles(user);

            String accessToken = jwtService.generateToken(user.getId(), user.getEmail(), roles);
            String refreshToken = jwtService.generateRefreshToken(user.getId(), user.getEmail(), roles);

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
        Long userId = jwtService.extractUserId(refreshToken);
        String email = jwtService.extractEmail(refreshToken);
        List<String> roles = jwtService.extractRolesFromToken(refreshToken);

        String accessToken = jwtService.generateToken(userId, email, roles);
        String newRefreshToken = jwtService.generateRefreshToken(userId, email, roles);
        log.info("Refresh token обновлён для пользователя: {}", email);

        return new AuthResponse(accessToken, newRefreshToken,"Bearer");
    }

    /**
     * Аутентификация с возвратом данных пользователя + токенов
     * Используется для redirect на HTML страницу
     */
    public Map<String, Object> authenticateWithUserData(SimpleAuthRequest authRequest) {
        UserDtoResponse user = authenticateUser(authRequest);

        List<String> roles = roleExtractor.extractRoles(user);

        // Генерируем JWT токены
        String accessToken = jwtService.generateToken(user.getId(), user.getEmail(), roles);
        String refreshToken = jwtService.generateRefreshToken(user.getId(), user.getEmail(), roles);

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

    public AuthResponse authenticateLegalEntity(LegalAuthRequest request) {
        String url = userServiceUrl + "/api/v1/legal-entities/authenticate";
        try {
            ResponseEntity<LegalEntityAuthResponse> response = restTemplate.postForEntity(
                    url, request, LegalEntityAuthResponse.class);
            LegalEntityAuthResponse legal = response.getBody();
            if (legal == null) throw new BadCredentialsException("Не удалось получить данные юрлица");

            String accessToken = jwtService.generateToken(legal.id(), legal.email(), "B2B");
            String refreshToken = jwtService.generateRefreshToken(legal.id(), legal.email(), "B2B");
            log.info("B2B login: legalEntityId={}, email={}", legal.id(), legal.email());
            return new AuthResponse(accessToken, refreshToken, "Bearer");
        } catch (HttpClientErrorException.Forbidden e) {
            throw new BadCredentialsException("Юрлицо не прошло верификацию");
        } catch (HttpClientErrorException.NotFound e) {
            throw new BadCredentialsException("Юрлицо не найдено");
        } catch (HttpClientErrorException e) {
            log.error("Ошибка обращения к user-service: {}", e.getMessage());
            throw new RuntimeException("Ошибка аутентификации. Попробуйте позже");
        }
    }

    @SuppressWarnings("unchecked")
    public AuthResponse switchContext(String bearerToken, SwitchContextRequest request) {
        Long userId = jwtService.extractUserId(bearerToken);

        String linkStatusUrl = userServiceUrl + "/api/v1/legal-entities/link-status/"
                + userId + "/" + request.legalEntityId();
        try {
            ResponseEntity<Map> linkResp = restTemplate.getForEntity(linkStatusUrl, Map.class);
            Boolean confirmed = linkResp.getBody() != null
                    && Boolean.TRUE.equals(linkResp.getBody().get("confirmed"));
            if (!confirmed) throw new org.springframework.security.access.AccessDeniedException("Нет подтверждённой связи");

            String legalUrl = userServiceUrl + "/api/v1/legal-entities/" + request.legalEntityId();
            ResponseEntity<LegalEntityDto> legalResp = restTemplate.getForEntity(legalUrl, LegalEntityDto.class);
            LegalEntityDto legal = legalResp.getBody();
            if (legal == null) throw new RuntimeException("Не удалось получить данные юрлица");

            String accessToken = jwtService.generateToken(request.legalEntityId(), legal.getEmail(), "B2B");
            String refreshToken = jwtService.generateRefreshToken(request.legalEntityId(), legal.getEmail(), "B2B");
            log.info("Switch context: userId={} -> legalEntityId={}", userId, request.legalEntityId());
            return new AuthResponse(accessToken, refreshToken, "Bearer");
        } catch (HttpClientErrorException e) {
            log.error("Ошибка switch-context: {}", e.getMessage());
            throw new RuntimeException("Ошибка переключения контекста");
        }
    }

    /**
     * Получение пользователя по email из user-service
     */
    private UserDtoResponse getUserByEmail(String email) {
        String userUrl = userServiceUrl + "/v1/users/email/" + email;

        try {
            ResponseEntity<UserDtoResponse> response = restTemplate.getForEntity(
                    userUrl,
                    UserDtoResponse.class
            );

            UserDtoResponse user = response.getBody();
            if (user == null) {
                throw new RuntimeException("Не удалось получить данные пользователя");
            }
            return user;

        } catch (HttpClientErrorException e) {
            log.error("Ошибка при получении пользователя по email: {}", e.getMessage());
            throw new RuntimeException("Ошибка получения данных пользователя");
        }
    }
}
