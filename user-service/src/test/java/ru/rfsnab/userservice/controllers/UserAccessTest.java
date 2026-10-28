package ru.rfsnab.userservice.controllers;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import ru.rfsnab.userservice.models.UserEntity;
import ru.rfsnab.userservice.models.dto.UpdateUserRequest;
import ru.rfsnab.userservice.services.LegalEntityService;
import ru.rfsnab.userservice.services.UserService;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Доступ к эндпоинтам пользователя. Поднимает MethodSecurityTestConfig, чтобы реально сработали @PreAuthorize.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(MethodSecurityTestConfig.class)
@ActiveProfiles("test")
@DisplayName("Доступ к эндпоинтам пользователя")
class UserAccessTest {

    private static final long USER_ID = 10L;
    private static final String B2B_AUTHORITY = "CLIENT_B2B";
    private static final String UPDATE_URL = "/v1/users/" + USER_ID;
    private static final String UPDATE_BODY = """
            {"firstname":"Иван","lastname":"Петров","surname":"Сидорович","phone":"79001112233",
             "email":"hacker@evil.com","password":"newpassword","roles":[{"id":2,"name":"ROLE_ADMIN"}]}
            """;
    private static final String OAUTH2_BODY = """
            {"email":"oauth@test.com","password":"password123","firstname":"A","lastname":"B","phone":"79001112233"}
            """;
    private static final String AUTH_BODY = "{\"email\":\"a@test.com\",\"password\":\"password123\"}";
    private static final String PASSWORD_BODY = "{\"passwordHash\":\"hash\"}";
    private static final String LEGAL_AUTH_BODY = "{\"login\":\"company\",\"password\":\"secret\"}";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private LegalEntityService legalEntityService;

    @BeforeEach
    void setUp() {
        UserEntity user = UserEntity.builder().id(USER_ID).email("user@test.com").firstname("Иван").build();
        when(userService.updateUser(anyLong(), any(UpdateUserRequest.class))).thenReturn(user);
        when(userService.findUserById(USER_ID)).thenReturn(Optional.of(user));
        when(userService.findUserByEmail("oauth@test.com")).thenReturn(Optional.of(user));
    }

    @Test
    @WithMockUser(username = "11", authorities = "ROLE_USER")
    @DisplayName("PUT /v1/users/{id}: чужой профиль -> 403")
    void updateForeignUserIsForbidden() throws Exception {
        mockMvc.perform(put(UPDATE_URL).contentType(MediaType.APPLICATION_JSON).content(UPDATE_BODY))
                .andExpect(status().isForbidden());

        verify(userService, never()).updateUser(anyLong(), any());
    }

    @Test
    @WithMockUser(username = "10", authorities = {"ROLE_USER", B2B_AUTHORITY})
    @DisplayName("PUT /v1/users/{id}: B2B-токен с совпавшим id -> 403")
    void updateByB2bWithSameIdIsForbidden() throws Exception {
        mockMvc.perform(put(UPDATE_URL).contentType(MediaType.APPLICATION_JSON).content(UPDATE_BODY))
                .andExpect(status().isForbidden());

        verify(userService, never()).updateUser(anyLong(), any());
    }

    @Test
    @WithMockUser(username = "10", authorities = "ROLE_USER")
    @DisplayName("PUT /v1/users/{id}: свой профиль -> 200, в сервис уходят только 4 поля профиля")
    void updateOwnProfileIsAllowed() throws Exception {
        mockMvc.perform(put(UPDATE_URL).contentType(MediaType.APPLICATION_JSON).content(UPDATE_BODY))
                .andExpect(status().isOk());

        verify(userService).updateUser(USER_ID, new UpdateUserRequest("Иван", "Петров", "Сидорович", "79001112233"));
    }

    @Test
    @WithMockUser(username = "1", authorities = "ROLE_ADMIN")
    @DisplayName("PUT /v1/users/{id}: ADMIN -> 200")
    void updateByAdminIsAllowed() throws Exception {
        mockMvc.perform(put(UPDATE_URL).contentType(MediaType.APPLICATION_JSON).content(UPDATE_BODY))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "10", authorities = "ROLE_USER")
    @DisplayName("GET /v1/users/all: не админ -> 403")
    void getAllUsersForbiddenForUser() throws Exception {
        mockMvc.perform(get("/v1/users/all")).andExpect(status().isForbidden());

        verify(userService, never()).findAllUsers();
    }

    @Test
    @WithMockUser(username = "1", authorities = "ROLE_ADMIN")
    @DisplayName("GET /v1/users/all: ADMIN -> 200")
    void getAllUsersAllowedForAdmin() throws Exception {
        mockMvc.perform(get("/v1/users/all")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "10", authorities = {"ROLE_USER", B2B_AUTHORITY})
    @DisplayName("/v1/users/me и /me/resend-verification с B2B-токеном -> 403")
    void meForbiddenForB2b() throws Exception {
        mockMvc.perform(get("/v1/users/me")).andExpect(status().isForbidden());
        mockMvc.perform(post("/v1/users/me/resend-verification")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "10", authorities = "ROLE_USER")
    @DisplayName("GET /v1/users/me с B2C-токеном -> 200")
    void meAllowedForB2c() throws Exception {
        mockMvc.perform(get("/v1/users/me")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "10", authorities = {"ROLE_USER", B2B_AUTHORITY})
    @DisplayName("/v1/users/me/legal-entities/* с B2B-токеном -> 403")
    void myLegalEntitiesForbiddenForB2b() throws Exception {
        mockMvc.perform(get("/v1/users/me/legal-entities")).andExpect(status().isForbidden());
        mockMvc.perform(post("/v1/users/me/legal-entities/link")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"inn\":\"1234567890\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/v1/users/me/legal-entities/resend-link")).andExpect(status().isForbidden());
        mockMvc.perform(post("/v1/users/me/legal-entities/resend-verification")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "10", authorities = "ROLE_USER")
    @DisplayName("GET /v1/users/me/legal-entities с B2C-токеном -> 200")
    void myLegalEntitiesAllowedForB2c() throws Exception {
        mockMvc.perform(get("/v1/users/me/legal-entities")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "10", authorities = "ROLE_USER")
    @DisplayName("authenticate, oauth2-login и legal authenticate обычному пользователю -> 403")
    void authenticateEndpointsForbiddenForUser() throws Exception {
        mockMvc.perform(post("/v1/users/authenticate").contentType(MediaType.APPLICATION_JSON).content(AUTH_BODY))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/v1/users/oauth2-login").contentType(MediaType.APPLICATION_JSON).content(OAUTH2_BODY))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/legal-entities/authenticate").contentType(MediaType.APPLICATION_JSON)
                        .content(LEGAL_AUTH_BODY))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("oauth2-login и legal authenticate без токена -> 401")
    void authenticateEndpointsRejectAnonymous() throws Exception {
        mockMvc.perform(post("/v1/users/oauth2-login").contentType(MediaType.APPLICATION_JSON).content(OAUTH2_BODY))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/legal-entities/authenticate").contentType(MediaType.APPLICATION_JSON)
                        .content(LEGAL_AUTH_BODY))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "internal-service", authorities = "ROLE_INTERNAL")
    @DisplayName("oauth2-login и legal authenticate для INTERNAL -> 200")
    void authenticateEndpointsAllowedForInternal() throws Exception {
        mockMvc.perform(post("/v1/users/oauth2-login").contentType(MediaType.APPLICATION_JSON).content(OAUTH2_BODY))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/legal-entities/authenticate").contentType(MediaType.APPLICATION_JSON)
                        .content(LEGAL_AUTH_BODY))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "10", authorities = "ROLE_USER")
    @DisplayName("profile-completeness, account-by-email и PUT password обычному пользователю -> 403")
    void internalOnlyEndpointsForbiddenForUser() throws Exception {
        mockMvc.perform(get("/v1/users/10/profile-completeness")).andExpect(status().isForbidden());
        mockMvc.perform(get("/v1/users/account-by-email").param("email", "oauth@test.com"))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/v1/users/10/password").contentType(MediaType.APPLICATION_JSON).content(PASSWORD_BODY))
                .andExpect(status().isForbidden());

        verify(userService, never()).setPasswordHash(anyLong(), any());
    }

    @Test
    @WithMockUser(username = "internal-service", authorities = "ROLE_INTERNAL")
    @DisplayName("profile-completeness, account-by-email и PUT password для INTERNAL -> 200")
    void internalOnlyEndpointsAllowedForInternal() throws Exception {
        mockMvc.perform(get("/v1/users/10/profile-completeness")).andExpect(status().isOk());
        mockMvc.perform(get("/v1/users/account-by-email").param("email", "oauth@test.com"))
                .andExpect(status().isOk());
        mockMvc.perform(put("/v1/users/10/password").contentType(MediaType.APPLICATION_JSON).content(PASSWORD_BODY))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "10", authorities = "ROLE_USER")
    @DisplayName("POST на GET-эндпоинт -> 405")
    void wrongMethodReturnsMethodNotAllowed() throws Exception {
        mockMvc.perform(post("/v1/users/me")).andExpect(status().isMethodNotAllowed());
    }
}
