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

            return AuthResponse.builder()
                    .accessToken(accessToken)
                    .refreshToken(refreshToken)
                    .tokenType("Bearer")
                    .clientType("B2C")
                    .build();
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

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(newRefreshToken)
                .tokenType("Bearer")
                .clientType("B2C")
                .build();
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
        result.put("clientType", "B2C");
        result.put("companyName", null);
        result.put("inn", null);

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

            String accessToken = jwtService.generateToken(legal.id(), legal.email(), "B2B", legal.companyName(), legal.inn());
            String refreshToken = jwtService.generateRefreshToken(legal.id(), legal.email(), "B2B", legal.companyName(), legal.inn());
            log.info("B2B login: legalEntityId={}, email={}", legal.id(), legal.email());
            return AuthResponse.builder()
                    .accessToken(accessToken)
                    .refreshToken(refreshToken)
                    .tokenType("Bearer")
                    .clientType("B2B")
                    .companyName(legal.companyName())
                    .inn(legal.inn())
                    .build();
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
        if ("B2B".equals(request.targetType())) {
            // B2C → B2B: no password required, find linked legal entity
            Long userId = jwtService.extractUserId(bearerToken);
            String linkUrl = userServiceUrl + "/api/v1/legal-entities/link-status/" + userId;
            ResponseEntity<Map> linkResp = restTemplate.getForEntity(linkUrl, Map.class);
            if (linkResp.getBody() == null || !Boolean.TRUE.equals(linkResp.getBody().get("confirmed"))) {
                throw new org.springframework.security.access.AccessDeniedException("Нет подтверждённой связи с юрлицом");
            }
            Long legalEntityId = ((Number) linkResp.getBody().get("legalEntityId")).longValue();

            String legalUrl = userServiceUrl + "/api/v1/legal-entities/" + legalEntityId;
            LegalEntityDto legal = restTemplate.getForEntity(legalUrl, LegalEntityDto.class).getBody();
            if (legal == null) throw new RuntimeException("Не удалось получить данные юрлица");

            String accessToken = jwtService.generateToken(legalEntityId, legal.getEmail(), "B2B", legal.getFullName(), legal.getInn());
            String refreshToken = jwtService.generateRefreshToken(legalEntityId, legal.getEmail(), "B2B", legal.getFullName(), legal.getInn());
            log.info("Switch context B2C→B2B: userId={} -> legalEntityId={}", userId, legalEntityId);

            return AuthResponse.builder()
                    .accessToken(accessToken).refreshToken(refreshToken).tokenType("Bearer")
                    .clientType("B2B").companyName(legal.getFullName()).inn(legal.getInn())
                    .build();

        } else {
            // B2B → B2C: password required
            if (request.password() == null || request.password().isBlank()) {
                throw new BadCredentialsException("Для переключения на личный аккаунт требуется пароль");
            }
            String email = jwtService.extractEmail(bearerToken);
            UserDtoResponse user = authenticateUser(new SimpleAuthRequest(email, request.password()));

            List<String> roles = roleExtractor.extractRoles(user);
            String accessToken = jwtService.generateToken(user.getId(), user.getEmail(), roles);
            String refreshToken = jwtService.generateRefreshToken(user.getId(), user.getEmail(), roles);
            log.info("Switch context B2B→B2C: legalEmail={} -> userId={}", email, user.getId());

            return AuthResponse.builder()
                    .accessToken(accessToken).refreshToken(refreshToken).tokenType("Bearer")
                    .clientType("B2C")
                    .build();
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
