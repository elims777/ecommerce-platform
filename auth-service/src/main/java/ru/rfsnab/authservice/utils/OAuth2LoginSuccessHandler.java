package ru.rfsnab.authservice.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import ru.rfsnab.authservice.models.dto.RegistrationRequest;
import ru.rfsnab.authservice.models.dto.UserDtoResponse;

import java.io.IOException;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class OAuth2LoginSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final RestTemplate restTemplate;
    private final JWTService jwtService;
    private final PasswordEncoder passwordEncoder;
    private final ObjectMapper objectMapper;

    @Value("${user.service.url}")
    private String userServiceUrl;

    private static final String CHARACTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%^&*";
    private static final int TEMP_PASSWORD_LENGTH = 16;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException {

        log.info("=== OAuth2LoginSuccessHandler вызван ===");
        log.info("Request URI: {}", request.getRequestURI());

        OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();
        log.info("OAuth2User attributes: {}", oAuth2User.getAttributes());

        // Извлекаем данные пользователя из Yandex
        String email = extractEmail(oAuth2User);
        String firstName = extractFirstName(oAuth2User);
        String lastName = extractLastName(oAuth2User);

        log.info("OAuth2 login для email: {}, firstname: {}, lastname: {}", email, firstName, lastName);

        try {
            // Проверяем существует ли пользователь в user-service
            UserDtoResponse user = oauth2LoginOrRegister(email, firstName, lastName);

            // Генерируем JWT tokens
            String accessToken = jwtService.generateToken(user.getEmail());
            String refreshToken = jwtService.generateRefreshToken(user.getEmail());

            // Формируем данные для передачи на страницу успеха
            Map<String, Object> tokens = new HashMap<>();
            tokens.put("access_token", accessToken);
            tokens.put("refresh_token", refreshToken);
            tokens.put("token_type", "Bearer");
            tokens.put("expires_in", 3600);
            tokens.put("user", user);

            log.info("Токены сгенерированы успешно");
            log.info("Access token: {}...", accessToken.substring(0, Math.min(20, accessToken.length())));

            // ВРЕМЕННО: Возвращаем JSON напрямую для тестирования
//            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
//            response.setStatus(HttpServletResponse.SC_OK);
//            response.getWriter().write(objectMapper.writeValueAsString(tokens));


             String tokensJson = objectMapper.writeValueAsString(tokens);
             String redirectUrl = "/oauth2/success?data=" +
                 java.net.URLEncoder.encode(tokensJson, java.nio.charset.StandardCharsets.UTF_8);
             log.info("Редирект на: {}", redirectUrl);
             response.sendRedirect(redirectUrl);

        } catch (Exception e) {
            log.error("=== Ошибка в OAuth2LoginSuccessHandler ===");
            log.error("Ошибка при обработке OAuth2 login: {}", e.getMessage(), e);

            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", "OAuth2 authentication failed");
            errorResponse.put("message", e.getMessage());

            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().write(objectMapper.writeValueAsString(errorResponse));
        }
    }

    /**
     * Единый метод для OAuth2 login/register через user-service
     */
    private UserDtoResponse oauth2LoginOrRegister(String email, String firstName, String lastName) {
        String oauth2Url = userServiceUrl + "/v1/users/oauth2-login";

        // Генерируем и хешируем временный пароль заранее
        String tempPassword = generateTemporaryPassword();
        String hashedPassword = passwordEncoder.encode(tempPassword);

        // Используем существующий RegistrationRequest
        RegistrationRequest request = RegistrationRequest.builder()
                .email(email)
                .password(hashedPassword)
                .firstname(firstName)
                .lastname(lastName)
                .emailVerified(true)  // OAuth2 users уже верифицированы
                .build();

        try {
            ResponseEntity<UserDtoResponse> response = restTemplate.postForEntity(
                    oauth2Url,
                    request,
                    UserDtoResponse.class
            );

            UserDtoResponse user = response.getBody();
            if (user == null) {
                throw new IllegalStateException("Не удалось получить данные пользователя");
            }

            log.info("OAuth2 login/register успешен для: {}", email);
            return user;

        } catch (HttpClientErrorException e) {
            log.error("Ошибка при OAuth2 login/register: {} - {}", e.getStatusCode(), e.getMessage());
            throw new RuntimeException("Не удалось выполнить OAuth2 аутентификацию: " + e.getMessage());
        }
    }

    /**
     * Генерирует криптографически стойкий временный пароль
     */
    private String generateTemporaryPassword() {
        SecureRandom random = new SecureRandom();
        StringBuilder password = new StringBuilder(TEMP_PASSWORD_LENGTH);

        for (int i = 0; i < TEMP_PASSWORD_LENGTH; i++) {
            password.append(CHARACTERS.charAt(random.nextInt(CHARACTERS.length())));
        }

        return password.toString();
    }

    /**
     * Извлекает email из OAuth2User (Yandex)
     */
    private String extractEmail(OAuth2User oAuth2User) {
        String email = oAuth2User.getAttribute("default_email");
        if (email == null) {
            throw new IllegalStateException("Email не найден в OAuth2 response от провайдера");
        }
        return email;
    }

    /**
     * Извлекает имя из OAuth2User
     */
    private String extractFirstName(OAuth2User oAuth2User) {
        String firstName = oAuth2User.getAttribute("first_name");
        if(firstName==null){
            firstName = "Гость";
        }
        return firstName;
    }

    /**
     * Извлекает фамилию из OAuth2User
     */
    private String extractLastName(OAuth2User oAuth2User) {
        String lastName = oAuth2User.getAttribute("last_name");
        if (lastName == null) {
            lastName = "Фамилия";
        }
        return lastName;
    }
}
