package ru.rfsnab.authservice.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;
import ru.rfsnab.authservice.models.dto.RegistrationRequest;
import ru.rfsnab.authservice.models.dto.RoleEntity;
import ru.rfsnab.authservice.models.dto.UserDtoResponse;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("OAuth2LoginSuccessHandler Unit Tests")
class OAuth2LoginSuccessHandlerTest {

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private JWTService jwtService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private RoleExtractor roleExtractor;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private Authentication authentication;

    @Mock
    private OAuth2User oAuth2User;

    private OAuth2LoginSuccessHandler handler;
    private ObjectMapper objectMapper;

    private final static String USER_SERVICE_URL = "http://localhost:8081";

    @BeforeEach
    void setUp(){
        objectMapper = new ObjectMapper();

        handler = new OAuth2LoginSuccessHandler(
                restTemplate,
                jwtService,
                passwordEncoder,
                objectMapper,
                roleExtractor
        );
        ReflectionTestUtils.setField(handler, "userServiceUrl", USER_SERVICE_URL);
    }

    @Test
    @DisplayName("onAuthenticationSuccess() - успешная OAuth2 аутентификация с редиректом")
    void onAuthenticationSuccess_Success_RedirectsWithTokens() throws Exception{
        String email = "oauth@example.com";
        String firstName = "OAuth";
        String lastName = "User";
        String accessToken = "access.token";
        String refreshToken = "refresh.token";

        when(request.getRequestURI()).thenReturn("/login/oauth2/code/yandex");
        when(authentication.getPrincipal()).thenReturn(oAuth2User);
        when(oAuth2User.getAttributes()).thenReturn(Map.of(
                "default_email", email,
                "first_name", firstName,
                "last_name", lastName
        ));
        when(oAuth2User.getAttribute("default_email")).thenReturn(email);
        when(oAuth2User.getAttribute("first_name")).thenReturn(firstName);
        when(oAuth2User.getAttribute("last_name")).thenReturn(lastName);

        RoleEntity userRole = new RoleEntity(1L, "ROLE_USER");

        UserDtoResponse user = new UserDtoResponse();
        user.setId(1L);
        user.setEmail(email);
        user.setFirstname(firstName);
        user.setLastname(lastName);
        user.setRoles(Set.of(userRole));

        when(passwordEncoder.encode(anyString())).thenReturn("hashed_password");
        when(restTemplate.postForEntity(
                eq(USER_SERVICE_URL+"/v1/users/oauth2-login"),
                any(RegistrationRequest.class),
                eq(UserDtoResponse.class)
        )).thenReturn(ResponseEntity.ok(user));
        when(roleExtractor.extractRoles(user)).thenReturn(List.of("ROLE_USER"));
        when(jwtService.generateToken(user.getId(), email,List.of("ROLE_USER"))).thenReturn(accessToken);
        when(jwtService.generateRefreshToken(user.getId(), email, List.of("ROLE_USER"))).thenReturn(refreshToken);

        handler.onAuthenticationSuccess(request, response, authentication);

        verify(response).sendRedirect(contains("/oauth2/success?data="));
        verify(jwtService).generateToken(user.getId(), email, List.of("ROLE_USER"));
        verify(jwtService).generateRefreshToken(user.getId(), email, List.of("ROLE_USER"));
    }

    @Test
    @DisplayName("onAuthenticationSuccess() - отсутствует email → выбрасывает исключение")
    void onAuthenticationSuccess_MissingEmail_ThrowsException() {
        // Given
        when(request.getRequestURI()).thenReturn("/login/oauth2/code/yandex");
        when(authentication.getPrincipal()).thenReturn(oAuth2User);
        when(oAuth2User.getAttributes()).thenReturn(Map.of());
        when(oAuth2User.getAttribute("default_email")).thenReturn(null);

        // When & Then
        assertThatThrownBy(() -> handler.onAuthenticationSuccess(request, response, authentication))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Email не найден");
    }

    @Test
    @DisplayName("onAuthenticationSuccess() - user-service недоступен → возвращает ошибку")
    void onAuthenticationSuccess_UserServiceError_ReturnsError() throws Exception {
        // Given
        String email = "oauth@example.com";
        StringWriter stringWriter = new StringWriter();
        PrintWriter printWriter = new PrintWriter(stringWriter);

        when(request.getRequestURI()).thenReturn("/login/oauth2/code/yandex");
        when(authentication.getPrincipal()).thenReturn(oAuth2User);
        when(oAuth2User.getAttributes()).thenReturn(Map.of("default_email", email));
        when(oAuth2User.getAttribute("default_email")).thenReturn(email);
        when(oAuth2User.getAttribute("first_name")).thenReturn(null);
        when(oAuth2User.getAttribute("last_name")).thenReturn(null);

        when(passwordEncoder.encode(anyString())).thenReturn("hashed_password");
        when(restTemplate.postForEntity(anyString(), any(), eq(UserDtoResponse.class)))
                .thenThrow(new RuntimeException("User service unavailable"));
        when(response.getWriter()).thenReturn(printWriter);

        // When
        handler.onAuthenticationSuccess(request, response, authentication);

        // Then
        verify(response).setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        assertThat(stringWriter.toString()).contains("error");
    }

    @Test
    @DisplayName("onAuthenticationSuccess() - user-service возвращает null → возвращает ошибку")
    void onAuthenticationSuccess_NullUserResponse_ReturnsError() throws Exception {
        // Given
        String email = "oauth@example.com";
        StringWriter stringWriter = new StringWriter();
        PrintWriter printWriter = new PrintWriter(stringWriter);

        when(request.getRequestURI()).thenReturn("/login/oauth2/code/yandex");
        when(authentication.getPrincipal()).thenReturn(oAuth2User);
        when(oAuth2User.getAttributes()).thenReturn(Map.of("default_email", email));
        when(oAuth2User.getAttribute("default_email")).thenReturn(email);
        when(oAuth2User.getAttribute("first_name")).thenReturn("Test");
        when(oAuth2User.getAttribute("last_name")).thenReturn("User");

        when(passwordEncoder.encode(anyString())).thenReturn("hashed_password");
        when(restTemplate.postForEntity(anyString(), any(), eq(UserDtoResponse.class)))
                .thenReturn(ResponseEntity.ok(null));
        when(response.getWriter()).thenReturn(printWriter);

        // When
        handler.onAuthenticationSuccess(request, response, authentication);

        // Then
        verify(response).setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        assertThat(stringWriter.toString()).contains("error");
    }

    @Test
    @DisplayName("onAuthenticationSuccess() - отсутствует first_name → используется дефолт")
    void onAuthenticationSuccess_MissingFirstName_UsesDefault() throws Exception {
        // Given
        String email = "oauth@example.com";
        String accessToken = "access.token";
        String refreshToken = "refresh.token";

        when(request.getRequestURI()).thenReturn("/login/oauth2/code/yandex");
        when(authentication.getPrincipal()).thenReturn(oAuth2User);
        when(oAuth2User.getAttributes()).thenReturn(Map.of("default_email", email));
        when(oAuth2User.getAttribute("default_email")).thenReturn(email);
        when(oAuth2User.getAttribute("first_name")).thenReturn(null); // нет имени
        when(oAuth2User.getAttribute("last_name")).thenReturn(null);  // нет фамилии

        RoleEntity userRole = new RoleEntity();
        userRole.setId(1L);
        userRole.setName("ROLE_USER");

        UserDtoResponse user = new UserDtoResponse();
        user.setId(1L);
        user.setEmail(email);
        user.setFirstname("Гость");
        user.setLastname("Фамилия");
        user.setRoles(Set.of(userRole));

        when(passwordEncoder.encode(anyString())).thenReturn("hashed_password");
        when(restTemplate.postForEntity(anyString(), any(RegistrationRequest.class), eq(UserDtoResponse.class)))
                .thenReturn(ResponseEntity.ok(user));

        when(roleExtractor.extractRoles(user)).thenReturn(List.of("ROLE_USER"));
        when(jwtService.generateToken(user.getId(), email, List.of("ROLE_USER"))).thenReturn(accessToken);
        when(jwtService.generateRefreshToken(user.getId(), email, List.of("ROLE_USER"))).thenReturn(refreshToken);

        // When
        handler.onAuthenticationSuccess(request, response, authentication);

        // Then
        verify(response).sendRedirect(contains("/oauth2/success?data="));
        verify(restTemplate).postForEntity(
                eq(USER_SERVICE_URL + "/v1/users/oauth2-login"),
                argThat((RegistrationRequest req) ->
                        "Гость".equals(req.getFirstname()) && "Фамилия".equals(req.getLastname())),
                eq(UserDtoResponse.class)
        );
    }
}