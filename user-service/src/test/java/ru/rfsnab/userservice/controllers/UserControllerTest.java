package ru.rfsnab.userservice.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import ru.rfsnab.userservice.exceptions.InvalidUnsubscribeTokenException;
import ru.rfsnab.userservice.exceptions.UserAlreadyExistsException;
import ru.rfsnab.userservice.models.LegalEntity;
import ru.rfsnab.userservice.models.RoleEntity;
import ru.rfsnab.userservice.models.UserEntity;
import ru.rfsnab.userservice.models.dto.RegistrationRequest;
import ru.rfsnab.userservice.models.dto.SetPasswordRequest;
import ru.rfsnab.userservice.models.dto.SimpleAuthRequest;
import ru.rfsnab.userservice.models.dto.UpdateUserRequest;
import ru.rfsnab.userservice.services.LegalEntityService;
import ru.rfsnab.userservice.services.UserService;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration тесты для UserController
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestSecurityConfig.class)
@ActiveProfiles("test")
@DisplayName("UserController integration tests")
class UserControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private LegalEntityService legalEntityService;

    @MockitoBean
    private PasswordEncoder passwordEncoder;

    private UserEntity testUser;
    private RoleEntity roleUser;

    @BeforeEach
    void setUp(){
        roleUser = RoleEntity.builder()
                .id(1L)
                .name("ROLE_USER")
                .build();

        testUser = UserEntity.builder()
                .id(1L)
                .email("test@test.com")
                .password("encodedPassword")
                .firstname("Firstname")
                .lastname("Lastname")
                .surname("Surname")
                .phone("+79998882221")
                .emailVerified(false)
                .roles(new HashSet<>(Set.of(roleUser)))
                .build();
    }

    @Test
    @DisplayName("POST /v1/users/oauth2-login - existing user returns OK")
    void oauth2Login_ExistingUser_ReturnsOk() throws Exception {
        // Given
        RegistrationRequest request = new RegistrationRequest(
                "test@test.com",
                "Password123@",
                "Firstname",
                "Lastname",
                "Surname",
                "79991234567",
                true,
                false
        );

        when(userService.findUserByEmail(anyString())).thenReturn(Optional.of(testUser));

        // When & Then
        mockMvc.perform(post("/v1/users/oauth2-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(1)))
                .andExpect(jsonPath("$.email", is("test@test.com")))
                .andExpect(jsonPath("$.firstname", is("Firstname")));

        verify(userService, times(1)).findUserByEmail("test@test.com");
        verify(userService, never()).registerUser(any());
    }

    @Test
    @DisplayName("POST /v1/users/oauth2-login - new user creates and returns CREATED")
    void oauth2Login_NewUser_ReturnsCreated() throws Exception {
        // Given
        RegistrationRequest request = new RegistrationRequest(
                "newuser@test.com",
                "Password123@",
                "Firstname",
                "Lastname",
                "Surname",
                "79991234567",
                true,
                false
        );

        UserEntity newUser = UserEntity.builder()
                .id(2L)
                .email("newuser@test.com")
                .firstname("New")
                .lastname("User")
                .password("encodedPassword")
                .roles(new HashSet<>(Set.of(roleUser)))
                .build();

        when(userService.findUserByEmail(anyString())).thenReturn(Optional.empty());
        when(userService.registerUser(any(UserEntity.class))).thenReturn(newUser);

        // When & Then
        mockMvc.perform(post("/v1/users/oauth2-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", is(2)))
                .andExpect(jsonPath("$.email", is("newuser@test.com")))
                .andExpect(jsonPath("$.firstname", is("New")));

        verify(userService, times(1)).findUserByEmail("newuser@test.com");
        verify(userService, times(1)).registerUser(any(UserEntity.class));
    }

    @Test
    @DisplayName("POST /v1/users/signup - success returns CREATED")
    void registerUser_Success_ReturnsCreated() throws Exception {
        // Given
        RegistrationRequest request = new RegistrationRequest(
                "newuser@test.com",
                "Password123@",
                "Firstname",
                "Lastname",
                "Surname",
                "79991234567",
                true,
                false
        );

        UserEntity newUser = UserEntity.builder()
                .id(2L)
                .email("newuser@test.com")
                .firstname("New")
                .lastname("User")
                .password("encodedPassword")
                .roles(new HashSet<>(Set.of(roleUser)))
                .build();

        when(userService.findUserByEmail(anyString())).thenReturn(Optional.empty());
        when(userService.registerUser(any(UserEntity.class))).thenReturn(newUser);

        // When & Then
        mockMvc.perform(post("/v1/users/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email", is("newuser@test.com")));

        verify(userService, times(1)).findUserByEmail("newuser@test.com");
        verify(userService, times(1)).registerUser(any(UserEntity.class));
    }

    @Test
    @DisplayName("POST /v1/users/signup - existing user throws UserAlreadyExistsException")
    void registerUser_ExistingUser_ThrowsException() throws Exception {
        // Given
        RegistrationRequest request = new RegistrationRequest(
                "test@test.com",
                "Password123@",
                "Firstname",
                "Lastname",
                "Surname",
                "79991234567",
                true,
                false
        );

        when(userService.findUserByEmail(anyString())).thenReturn(Optional.of(testUser));

        // When & Then
        mockMvc.perform(post("/v1/users/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict()); // UserAlreadyExistsException должен быть обработан как 409

        verify(userService, times(1)).findUserByEmail("test@test.com");
        verify(userService, never()).registerUser(any());
    }

    @Test
    @DisplayName("POST /v1/users/authenticate - success returns OK")
    void authenticate_Success_ReturnsOk() throws Exception {
        // Given
        SimpleAuthRequest request = new SimpleAuthRequest(
                "test@test.com",
                "password"
        );

        when(userService.findUserByEmail(anyString())).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(true);

        // When & Then
        mockMvc.perform(post("/v1/users/authenticate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email", is("test@test.com")));

        verify(userService, times(1)).findUserByEmail("test@test.com");
        verify(passwordEncoder, times(1)).matches("password", "encodedPassword");
    }

    @Test
    @DisplayName("POST /v1/users/authenticate - wrong password returns UNAUTHORIZED")
    void authenticate_WrongPassword_ReturnsUnauthorized() throws Exception {
        // Given
        SimpleAuthRequest request = new SimpleAuthRequest(
                "test@test.com",
                "wrongpassword"
        );

        when(userService.findUserByEmail(anyString())).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(false);

        // When & Then
        mockMvc.perform(post("/v1/users/authenticate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized()); // BadCredentialsException

        verify(userService, times(1)).findUserByEmail("test@test.com");
        verify(passwordEncoder, times(1)).matches("wrongpassword", "encodedPassword");
    }

    @Test
    @DisplayName("PUT /v1/users/{id} - success returns OK")
    void updateUser_Success_ReturnsOk() throws Exception {
        // Given
        UpdateUserRequest request = new UpdateUserRequest("Updated", "Name", null, "79001112233");

        UserEntity updatedUser = UserEntity.builder()
                .id(1L)
                .email("test@test.com")
                .firstname("Updated")
                .lastname("Name")
                .password("encodedPassword")
                .build();

        when(userService.updateUser(anyLong(), any(UpdateUserRequest.class))).thenReturn(updatedUser);

        // When & Then
        mockMvc.perform(put("/v1/users/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email", is("test@test.com")))
                .andExpect(jsonPath("$.firstname", is("Updated")));

        verify(userService, times(1)).updateUser(1L, request);
    }

    @Test
    @DisplayName("PUT /v1/users/{id} - имя длиннее 50 символов возвращает 400")
    void updateUser_TooLongFirstname_ReturnsBadRequest() throws Exception {
        String body = "{\"firstname\":\"" + "а".repeat(51) + "\",\"lastname\":\"Name\",\"phone\":\"79001112233\"}";

        mockMvc.perform(put("/v1/users/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());

        verify(userService, never()).updateUser(anyLong(), any());
    }

    @Test
    @DisplayName("PUT /v1/users/{id} - телефон занят другим пользователем возвращает 409")
    void updateUser_PhoneTaken_ReturnsConflict() throws Exception {
        when(userService.updateUser(anyLong(), any(UpdateUserRequest.class)))
                .thenThrow(new UserAlreadyExistsException("Пользователь с таким номером телефона уже существует"));

        mockMvc.perform(put("/v1/users/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateUserRequest("A", "B", null, "79001112233"))))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("PUT /v1/users/{id} - пустое имя и неверный телефон возвращают 400")
    void updateUser_InvalidBody_ReturnsBadRequest() throws Exception {
        mockMvc.perform(put("/v1/users/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstname\":\"\",\"lastname\":\"Name\",\"phone\":\"123\"}"))
                .andExpect(status().isBadRequest());

        verify(userService, never()).updateUser(anyLong(), any());
    }

    @Test
    @DisplayName("PATCH /v1/users/{id}/admin - имя длиннее 50 символов возвращает 400")
    void updateUserAdmin_TooLongFirstname_ReturnsBadRequest() throws Exception {
        String body = "{\"firstname\":\"" + "а".repeat(51) + "\",\"lastname\":\"Name\",\"phone\":\"\"}";

        mockMvc.perform(patch("/v1/users/1/admin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());

        verify(userService, never()).updateUserAdmin(anyLong(), any(), any(), any());
    }

    @Test
    @DisplayName("PATCH /v1/users/{id}/admin - пустой телефон проходит валидацию")
    void updateUserAdmin_EmptyPhone_ReturnsOk() throws Exception {
        when(userService.updateUserAdmin(1L, "A", "B", "")).thenReturn(testUser);

        mockMvc.perform(patch("/v1/users/1/admin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstname\":\"A\",\"lastname\":\"B\",\"phone\":\"\"}"))
                .andExpect(status().isOk());

        verify(userService).updateUserAdmin(1L, "A", "B", "");
    }

    @Test
    @DisplayName("PATCH /v1/users/{id}/admin - телефон не из 11 цифр возвращает 400")
    void updateUserAdmin_InvalidPhone_ReturnsBadRequest() throws Exception {
        mockMvc.perform(patch("/v1/users/1/admin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstname\":\"A\",\"lastname\":\"B\",\"phone\":\"123\"}"))
                .andExpect(status().isBadRequest());

        verify(userService, never()).updateUserAdmin(anyLong(), any(), any(), any());
    }

    @Test
    @DisplayName("PATCH /v1/users/{id}/admin - телефон занят другим пользователем возвращает 409")
    void updateUserAdmin_PhoneTaken_ReturnsConflict() throws Exception {
        when(userService.updateUserAdmin(anyLong(), any(), any(), any()))
                .thenThrow(new UserAlreadyExistsException("Пользователь с таким номером телефона уже существует"));

        mockMvc.perform(patch("/v1/users/1/admin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstname\":\"A\",\"lastname\":\"B\",\"phone\":\"79001112233\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("GET /v1/users/all - returns list of users")
    void getAllUsers_ReturnsListOfUsers() throws Exception {
        // Given
        UserEntity user2 = UserEntity.builder()
                .id(2L)
                .email("user2@test.com")
                .firstname("User")
                .lastname("Two")
                .password("encodedPassword")
                .build();

        when(userService.findAllUsers()).thenReturn(List.of(testUser, user2));

        // When & Then
        mockMvc.perform(get("/v1/users/all"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].email", is("test@test.com")))
                .andExpect(jsonPath("$[1].email", is("user2@test.com")));

        verify(userService, times(1)).findAllUsers();
    }

    @Test
    @DisplayName("GET /v1/users/me - returns current user")
    void getCurrentUser_ReturnsCurrentUser() throws Exception {
        // Given
        when(userService.findUserById(1L)).thenReturn(Optional.of(testUser));

        // When & Then
        mockMvc.perform(get("/v1/users/me")
                        .with(user("1")))  // userId, не email
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("test@test.com"));

        verify(userService, times(1)).findUserById(1L);
    }

    @Test
    @DisplayName("PUT /v1/users/{userId}/verify - success returns OK")
    void verifyUser_Success_ReturnsOk() throws Exception {
        // Given
        doNothing().when(userService).verifyUser(1L);

        // When & Then
        mockMvc.perform(put("/v1/users/1/verify"))
                .andExpect(status().isOk())
                .andExpect(content().string("User verified successfully"));

        verify(userService, times(1)).verifyUser(1L);
    }

    @Test
    @DisplayName("GET /v1/users/account-by-email - найдено физлицо возвращает USER")
    void accountByEmail_UserFound_ReturnsUser() throws Exception {
        when(userService.findUserByEmail("test@test.com")).thenReturn(Optional.of(testUser));

        mockMvc.perform(get("/v1/users/account-by-email").param("email", "test@test.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId", is(1)))
                .andExpect(jsonPath("$.accountType", is("USER")))
                .andExpect(jsonPath("$.email", is("test@test.com")))
                .andExpect(jsonPath("$.firstName", is("Firstname")));

        verify(userService, times(1)).findUserByEmail("test@test.com");
        verify(legalEntityService, never()).findByEmail(anyString());
    }

    @Test
    @DisplayName("GET /v1/users/account-by-email - физлица нет, найдено юрлицо возвращает LEGAL")
    void accountByEmail_LegalEntityFound_ReturnsLegal() throws Exception {
        LegalEntity legalEntity = LegalEntity.builder()
                .id(5L)
                .email("company@example.com")
                .fullName("ООО Ромашка")
                .password("encoded")
                .build();

        when(userService.findUserByEmail("company@example.com")).thenReturn(Optional.empty());
        when(legalEntityService.findByEmail("company@example.com")).thenReturn(Optional.of(legalEntity));

        mockMvc.perform(get("/v1/users/account-by-email").param("email", "company@example.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId", is(5)))
                .andExpect(jsonPath("$.accountType", is("LEGAL")))
                .andExpect(jsonPath("$.email", is("company@example.com")))
                .andExpect(jsonPath("$.firstName", is("ООО Ромашка")));

        verify(legalEntityService, times(1)).findByEmail("company@example.com");
    }

    @Test
    @DisplayName("GET /v1/users/account-by-email - ничего не найдено возвращает 404")
    void accountByEmail_NotFound_Returns404() throws Exception {
        when(userService.findUserByEmail("nobody@test.com")).thenReturn(Optional.empty());
        when(legalEntityService.findByEmail("nobody@test.com")).thenReturn(Optional.empty());

        mockMvc.perform(get("/v1/users/account-by-email").param("email", "nobody@test.com"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET несуществующего пути — 404, а не 500")
    void unknownPath_Returns404() throws Exception {
        mockMvc.perform(get("/v1/users/1/no-such-endpoint"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status", is(404)));
    }

    @Test
    @DisplayName("GET /v1/users/abc — нечисловой id возвращает 400, а не 500")
    void nonNumericId_Returns400() throws Exception {
        mockMvc.perform(get("/v1/users/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)));
    }

    @Test
    @DisplayName("GET /v1/users/account-by-email без email — 400, а не 500")
    void accountByEmail_MissingEmail_Returns400() throws Exception {
        mockMvc.perform(get("/v1/users/account-by-email"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)));
    }

    @Test
    @DisplayName("PUT /v1/users/{id} — битый JSON возвращает 400, а не 500")
    void updateUser_MalformedJson_Returns400() throws Exception {
        mockMvc.perform(put("/v1/users/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not-json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)));

        verify(userService, never()).updateUser(anyLong(), any());
    }

    @Test
    @DisplayName("GET /v1/users/unsubscribe - недействительный токен возвращает 400")
    void unsubscribe_InvalidToken_Returns400() throws Exception {
        doThrow(new InvalidUnsubscribeTokenException("Недействительная ссылка отписки"))
                .when(userService).unsubscribeByToken("bad");

        mockMvc.perform(get("/v1/users/unsubscribe").param("token", "bad"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)));
    }

    @Test
    @DisplayName("PUT /v1/users/{id}/password - записывает хеш как есть")
    void setPassword_Success_ReturnsOk() throws Exception {
        SetPasswordRequest request = new SetPasswordRequest("already-hashed-value");
        doNothing().when(userService).setPasswordHash(1L, "already-hashed-value");

        mockMvc.perform(put("/v1/users/1/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        verify(userService, times(1)).setPasswordHash(1L, "already-hashed-value");
        verify(passwordEncoder, never()).encode(anyString());
    }

    @Test
    @DisplayName("PUT /v1/users/{id}/password - пустой passwordHash возвращает 400")
    void setPassword_BlankHash_Returns400() throws Exception {
        SetPasswordRequest request = new SetPasswordRequest("");

        mockMvc.perform(put("/v1/users/1/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verify(userService, never()).setPasswordHash(anyLong(), anyString());
    }
}