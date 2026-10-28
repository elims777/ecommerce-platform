package ru.rfsnab.authservice.service;

import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import ru.rfsnab.authservice.exceptions.AuthServiceException;
import ru.rfsnab.authservice.exceptions.UserNotFoundException;
import ru.rfsnab.authservice.models.dto.AuthResponse;
import ru.rfsnab.authservice.models.dto.LegalAuthRequest;
import ru.rfsnab.authservice.models.dto.LegalEntityAuthResponse;
import ru.rfsnab.authservice.models.dto.LegalEntityDto;
import ru.rfsnab.authservice.models.dto.RoleEntity;
import ru.rfsnab.authservice.models.dto.SimpleAuthRequest;
import ru.rfsnab.authservice.models.dto.SwitchContextRequest;
import ru.rfsnab.authservice.models.dto.UserDtoResponse;
import ru.rfsnab.authservice.utils.EmailMasker;
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

    private static final String TOKEN_TYPE_BEARER = "Bearer";
    private static final String CLIENT_TYPE_B2B = "B2B";
    private static final String INTERNAL_TOKEN_HEADER = "X-Internal-Token";
    private static final String USER_SERVICE_ERROR_LOG = "Ошибка при обращении к user-service: {}";
    private static final String LEGAL_ENTITIES_URL = "/api/v1/legal-entities/";
    private static final String VERIFICATION_STATUS_VERIFIED = "VERIFIED";

    private final RestTemplate restTemplate;
    private final JWTService jwtService;
    private final RoleExtractor roleExtractor;

    @Value("${user.service.url}")
    private String userServiceUrl;
    @Value("${user.service.auth}")
    private String userServiceAuthUrl;
    @Value("${internal.secret}")
    private String internalSecret;

    /**
     * Аутентификация пользователя через user-service
     * Возвращает только токены
     */
    public AuthResponse authenticate(SimpleAuthRequest authRequest){
        try{
            ResponseEntity<UserDtoResponse> response = restTemplate.postForEntity(
                    userServiceUrl + userServiceAuthUrl,
                    internalRequest(authRequest),
                    UserDtoResponse.class
            );

            UserDtoResponse user = response.getBody();
            if(user==null){
                throw new BadCredentialsException("Не удалось получить данные пользователя");
            }

            List<String> roles = roleExtractor.extractRoles(user);

            String accessToken = jwtService.generateToken(user.getId(), user.getEmail(), roles);
            String refreshToken = jwtService.generateRefreshToken(user.getId(), user.getEmail(), roles);

            log.info("Успешная аутентификация пользователя: {}", EmailMasker.mask(user.getEmail()));

            return AuthResponse.builder()
                    .accessToken(accessToken)
                    .refreshToken(refreshToken)
                    .tokenType(TOKEN_TYPE_BEARER)
                    .clientType("B2C")
                    .build();
        } catch (HttpClientErrorException.Unauthorized | HttpClientErrorException.Forbidden e) {
            log.warn("Неудачная попытка входа: {}", EmailMasker.mask(authRequest.getEmail()));
            throw new BadCredentialsException("Неверный email или пароль");
        } catch (HttpClientErrorException e) {
            log.error(USER_SERVICE_ERROR_LOG, e.getMessage());
            throw new AuthServiceException("Ошибка аутентификации. Попробуйте позже");
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

        if (CLIENT_TYPE_B2B.equals(jwtService.extractClientType(refreshToken))) {
            assertLegalEntityActive(userId);
            String companyName = jwtService.extractCompanyName(refreshToken);
            String inn = jwtService.extractInn(refreshToken);
            log.info("Refresh token обновлён для юрлица: legalEntityId={}", userId);
            return AuthResponse.builder()
                    .accessToken(jwtService.generateToken(userId, email, CLIENT_TYPE_B2B, companyName, inn))
                    .refreshToken(jwtService.generateRefreshToken(userId, email, CLIENT_TYPE_B2B, companyName, inn))
                    .tokenType(TOKEN_TYPE_BEARER)
                    .clientType(CLIENT_TYPE_B2B)
                    .companyName(companyName)
                    .inn(inn)
                    .build();
        }

        List<String> roles = jwtService.extractRolesFromToken(refreshToken);

        String accessToken = jwtService.generateToken(userId, email, roles);
        String newRefreshToken = jwtService.generateRefreshToken(userId, email, roles);
        log.info("Refresh token обновлён для пользователя: {}", EmailMasker.mask(email));

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(newRefreshToken)
                .tokenType(TOKEN_TYPE_BEARER)
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

        log.info("Успешная аутентификация пользователя: {}", EmailMasker.mask(user.getEmail()));

        // Формируем полный response с данными пользователя
        Map<String, Object> result = new HashMap<>();
        result.put("access_token", accessToken);
        result.put("refresh_token", refreshToken);
        result.put("token_type", TOKEN_TYPE_BEARER);
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
                throw new AuthServiceException("Не удалось получить данные пользователя");
            }
            log.debug("Получены данные пользователя: {}", EmailMasker.mask(user.getEmail()));
            return user;
        } catch (HttpClientErrorException.Unauthorized e){
            log.warn("Невалидный токен при запросе данных пользователя");
            throw new JwtException("Токен недействителен или истек");
        } catch (HttpClientErrorException.NotFound e){
            log.warn("Пользователь не найден в user-service");
            throw new UserNotFoundException("Пользователь не найден");
        } catch (HttpClientErrorException.Forbidden e){
            throw new AccessDeniedException("Доступ запрещён");
        } catch (HttpClientErrorException e){
            log.error(USER_SERVICE_ERROR_LOG, e.getMessage());
            throw new AuthServiceException("Ошибка получения данных пользователя");
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
                    internalRequest(authRequest),
                    UserDtoResponse.class
            );

            UserDtoResponse user = response.getBody();

            if (user == null) {
                throw new BadCredentialsException("Не удалось получить данные пользователя");
            }

            return user;

        } catch (HttpClientErrorException.Unauthorized | HttpClientErrorException.Forbidden e) {
            log.warn("Неудачная попытка входа: {}", EmailMasker.mask(authRequest.getEmail()));
            throw new BadCredentialsException("Неверный email или пароль");
        } catch (HttpClientErrorException e) {
            log.error(USER_SERVICE_ERROR_LOG, e.getMessage());
            throw new AuthServiceException("Ошибка аутентификации. Попробуйте позже");
        }
    }

    public AuthResponse authenticateLegalEntity(LegalAuthRequest request) {
        String url = userServiceUrl + "/api/v1/legal-entities/authenticate";
        try {
            ResponseEntity<LegalEntityAuthResponse> response = restTemplate.postForEntity(
                    url, internalRequest(request), LegalEntityAuthResponse.class);
            LegalEntityAuthResponse legal = response.getBody();
            if (legal == null) throw new BadCredentialsException("Не удалось получить данные юрлица");

            String accessToken = jwtService.generateToken(legal.id(), legal.email(), "B2B", legal.companyName(), legal.inn());
            String refreshToken = jwtService.generateRefreshToken(legal.id(), legal.email(), "B2B", legal.companyName(), legal.inn());
            log.info("B2B login: legalEntityId={}, email={}", legal.id(), EmailMasker.mask(legal.email()));
            return AuthResponse.builder()
                    .accessToken(accessToken)
                    .refreshToken(refreshToken)
                    .tokenType(TOKEN_TYPE_BEARER)
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
            throw new AuthServiceException("Ошибка аутентификации. Попробуйте позже");
        }
    }

    @SuppressWarnings("unchecked")
    public AuthResponse switchContext(String bearerToken, SwitchContextRequest request) {
        if (CLIENT_TYPE_B2B.equals(request.targetType())) {
            // B2C → B2B: no password required, find linked legal entity
            if (CLIENT_TYPE_B2B.equals(jwtService.extractClientType(bearerToken))) {
                throw new AccessDeniedException("Переключение в B2B возможно только из личного аккаунта");
            }
            Long userId = jwtService.extractUserId(bearerToken);
            try {
                String linkUrl = userServiceUrl + "/api/v1/legal-entities/link-status/" + userId;
                ResponseEntity<Map> linkResp = restTemplate.exchange(linkUrl, HttpMethod.GET, internalRequest(), Map.class);
                if (linkResp.getBody() == null || !Boolean.TRUE.equals(linkResp.getBody().get("confirmed"))) {
                    throw new AccessDeniedException("Нет подтверждённой связи с юрлицом");
                }
                Long legalEntityId = ((Number) linkResp.getBody().get("legalEntityId")).longValue();

                String legalUrl = userServiceUrl + LEGAL_ENTITIES_URL + legalEntityId;
                LegalEntityDto legal = restTemplate.exchange(legalUrl, HttpMethod.GET, internalRequest(), LegalEntityDto.class).getBody();
                if (legal == null) throw new AuthServiceException("Не удалось получить данные юрлица");
                if (!VERIFICATION_STATUS_VERIFIED.equals(legal.getVerificationStatus())) {
                    throw new AccessDeniedException("Юрлицо не верифицировано");
                }

                String accessToken = jwtService.generateToken(legalEntityId, legal.getEmail(), CLIENT_TYPE_B2B, legal.getFullName(), legal.getInn());
                String refreshToken = jwtService.generateRefreshToken(legalEntityId, legal.getEmail(), CLIENT_TYPE_B2B, legal.getFullName(), legal.getInn());
                log.info("Switch context B2C→B2B: userId={} -> legalEntityId={}", userId, legalEntityId);

                return AuthResponse.builder()
                        .accessToken(accessToken).refreshToken(refreshToken).tokenType(TOKEN_TYPE_BEARER)
                        .clientType(CLIENT_TYPE_B2B).companyName(legal.getFullName()).inn(legal.getInn())
                        .build();
            } catch (HttpClientErrorException.NotFound e) {
                throw new AccessDeniedException("Юрлицо не найдено или не привязано к аккаунту");
            } catch (HttpClientErrorException e) {
                log.error(USER_SERVICE_ERROR_LOG, e.getMessage());
                throw new AuthServiceException("Ошибка переключения контекста. Попробуйте позже");
            }
        } else {
            throw new AccessDeniedException("Переход из организации в личный аккаунт невозможен — выйдите и войдите заново");
        }
    }

    /**
     * Перед продлением B2B-сессии проверяем, что юрлицо ещё существует и верифицировано.
     */
    private void assertLegalEntityActive(Long legalEntityId) {
        try {
            LegalEntityDto legal = restTemplate.exchange(userServiceUrl + LEGAL_ENTITIES_URL + legalEntityId,
                    HttpMethod.GET, internalRequest(), LegalEntityDto.class).getBody();
            if (legal == null || !VERIFICATION_STATUS_VERIFIED.equals(legal.getVerificationStatus())) {
                throw new JwtException("Юрлицо недоступно");
            }
        } catch (HttpClientErrorException.NotFound e) {
            throw new JwtException("Юрлицо не найдено");
        } catch (HttpClientErrorException e) {
            log.error(USER_SERVICE_ERROR_LOG, e.getMessage());
            throw new AuthServiceException("Ошибка обновления токена. Попробуйте позже");
        }
    }

    private HttpEntity<Void> internalRequest() {
        return internalRequest(null);
    }

    private <T> HttpEntity<T> internalRequest(T body) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(INTERNAL_TOKEN_HEADER, internalSecret);
        return new HttpEntity<>(body, headers);
    }
}
