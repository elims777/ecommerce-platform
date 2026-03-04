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
import ru.rfsnab.userservice.models.RoleEntity;
import ru.rfsnab.userservice.models.UserEntity;
import ru.rfsnab.userservice.models.dto.RegistrationRequest;
import ru.rfsnab.userservice.models.dto.SimpleAuthRequest;
import ru.rfsnab.userservice.models.dto.UserDto;
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
                true
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
                true
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
                true
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
                true
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
        UserDto userDto = UserDto.builder()
                .id(1L)
                .email("updated@test.com")
                .firstname("Updated")
                .lastname("Name")
                .build();

        UserEntity updatedUser = UserEntity.builder()
                .id(1L)
                .email("updated@test.com")
                .firstname("Updated")
                .lastname("Name")
                .password("encodedPassword")
                .build();

        when(userService.updateUser(anyLong(), any(UserEntity.class))).thenReturn(updatedUser);

        // When & Then
        mockMvc.perform(put("/v1/users/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(userDto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email", is("updated@test.com")))
                .andExpect(jsonPath("$.firstname", is("Updated")));

        verify(userService, times(1)).updateUser(eq(1L), any(UserEntity.class));
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
    @DisplayName("DELETE /v1/users/{id} - success returns OK")
    void deleteUser_Success_ReturnsOk() throws Exception {
        // Given
        when(userService.findUserById(1L)).thenReturn(Optional.of(testUser));
        doNothing().when(userService).deleteUser(1L);

        // When & Then
        mockMvc.perform(delete("/v1/users/1"))
                .andExpect(status().isOk())
                .andExpect(content().string("User deleted."));

        verify(userService, times(1)).findUserById(1L);
        verify(userService, times(1)).deleteUser(1L);
    }

    @Test
    @DisplayName("DELETE /v1/users/{id} - user not found returns NOT_FOUND")
    void deleteUser_NotFound_ReturnsNotFound() throws Exception {
        // Given
        when(userService.findUserById(999L)).thenReturn(Optional.empty());

        // When & Then
        mockMvc.perform(delete("/v1/users/999"))
                .andExpect(status().isNotFound()); // UsernameNotFoundException

        verify(userService, times(1)).findUserById(999L);
        verify(userService, never()).deleteUser(anyLong());
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
}