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
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class OAuth2LoginSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final RestTemplate restTemplate;
    private final JWTService jwtService;
    private final PasswordEncoder passwordEncoder;
    private final ObjectMapper objectMapper;
    private final RoleExtractor roleExtractor;

    @Value("${user.service.url}")
    private String userServiceUrl;

    @Value("${app.frontend-url:http://localhost:5173}")
    private String frontendUrl;

    private static final String CHARACTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%^&*";
    private static final int TEMP_PASSWORD_LENGTH = 16;
    private static final String PLACEHOLDER_PHONE = "00000000000";

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
        String phone = extractPhone(oAuth2User);

        log.info("OAuth2 login для email: {}, firstname: {}, lastname: {}", email, firstName, lastName);

        if (phone == null && isRegisterAction(request)) {
            log.warn("OAuth2 регистрация без доступа к телефону — отказ для email: {}", email);
            response.sendRedirect(frontendUrl + "/oauth2/error?reason=phone_required");
            return;
        }

        try {
            // Проверяем существует ли пользователь в user-service
            UserDtoResponse user = oauth2LoginOrRegister(email, firstName, lastName, phone);

            List<String> roles = roleExtractor.extractRoles(user);

            // Генерируем JWT tokens
            String accessToken = jwtService.generateToken(user.getId(), user.getEmail(), roles);
            String refreshToken = jwtService.generateRefreshToken(user.getId(), user.getEmail(), roles);

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
             String redirectUrl = frontendUrl + "/oauth2/success?data=" +
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
    private UserDtoResponse oauth2LoginOrRegister(String email, String firstName, String lastName, String phone) {
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
                .phone(phone != null ? phone : PLACEHOLDER_PHONE)  // реальный телефон из Яндекса, иначе placeholder
                .emailVerified(true)
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

    /**
     * Извлекает телефон из атрибута default_phone (Yandex), нормализует до 11 цифр.
     * Возвращает null, если пользователь не дал доступ к телефону.
     */
    @SuppressWarnings("unchecked")
    private String extractPhone(OAuth2User oAuth2User) {
        Object defaultPhone = oAuth2User.getAttribute("default_phone");
        if (!(defaultPhone instanceof Map<?, ?> phoneMap)) {
            return null;
        }
        Object number = ((Map<String, Object>) phoneMap).get("number");
        if (number == null) {
            return null;
        }
        String digits = number.toString().replaceAll("\\D", "");
        if (digits.length() == 11 && digits.startsWith("8")) {
            digits = "7" + digits.substring(1);
        }
        return digits.length() == 11 ? digits : null;
    }

    /**
     * true, если текущий OAuth2-поток — регистрация (флаг проставлен OAuth2RegisterController).
     */
    private boolean isRegisterAction(HttpServletRequest request) {
        var session = request.getSession(false);
        return session != null && "register".equals(session.getAttribute("oauth2_action"));
    }
}
