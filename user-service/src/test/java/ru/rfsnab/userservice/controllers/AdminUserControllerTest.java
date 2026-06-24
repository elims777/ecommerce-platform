package ru.rfsnab.userservice.controllers;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import ru.rfsnab.userservice.exceptions.OrderServiceUnavailableException;
import ru.rfsnab.userservice.models.RoleEntity;
import ru.rfsnab.userservice.models.UserEntity;
import ru.rfsnab.userservice.services.UserService;
import ru.rfsnab.userservice.services.client.OrderServiceClient;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestSecurityConfig.class)
@ActiveProfiles("test")
@DisplayName("AdminUserController integration tests")
class AdminUserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private OrderServiceClient orderServiceClient;

    private UserEntity targetUser;
    private UserEntity targetAdmin;

    @BeforeEach
    void setUp() {
        RoleEntity userRole = RoleEntity.builder().id(1L).name("ROLE_USER").build();
        RoleEntity adminRole = RoleEntity.builder().id(2L).name("ROLE_ADMIN").build();

        targetUser = UserEntity.builder()
                .id(42L)
                .email("user@test.com")
                .firstname("Test")
                .lastname("User")
                .roles(new HashSet<>(Set.of(userRole)))
                .build();

        targetAdmin = UserEntity.builder()
                .id(43L)
                .email("admin@test.com")
                .firstname("Admin")
                .lastname("User")
                .roles(new HashSet<>(Set.of(adminRole)))
                .build();
    }

    @Test
    @DisplayName("DELETE /api/v1/admin/users/{id} — успешное удаление обычного пользователя")
    void shouldDeleteRegularUser() throws Exception {
        when(userService.findUserById(42L)).thenReturn(Optional.of(targetUser));
        when(orderServiceClient.countActiveOrdersByUserId(42L, "1", "ROLE_ADMIN")).thenReturn(0L);

        mockMvc.perform(delete("/api/v1/admin/users/42")
                        .header("X-User-Id", "1")
                        .header("X-User-Role", "ROLE_ADMIN"))
                .andExpect(status().isNoContent());

        verify(userService).deleteUser(42L);
    }

    @Test
    @DisplayName("DELETE — 409 при попытке удалить самого себя")
    void shouldReturn409WhenDeletingSelf() throws Exception {
        mockMvc.perform(delete("/api/v1/admin/users/42")
                        .header("X-User-Id", "42")
                        .header("X-User-Role", "ROLE_ADMIN"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Нельзя удалить собственный аккаунт"));

        verify(userService, never()).deleteUser(anyLong());
    }

    @Test
    @DisplayName("DELETE — 404 если пользователь не найден")
    void shouldReturn404WhenUserNotFound() throws Exception {
        when(userService.findUserById(999L)).thenReturn(Optional.empty());

        mockMvc.perform(delete("/api/v1/admin/users/999")
                        .header("X-User-Id", "1")
                        .header("X-User-Role", "ROLE_ADMIN"))
                .andExpect(status().isNotFound());

        verify(userService, never()).deleteUser(anyLong());
    }

    @Test
    @DisplayName("DELETE — 409 при удалении последнего админа")
    void shouldReturn409WhenLastAdmin() throws Exception {
        when(userService.findUserById(43L)).thenReturn(Optional.of(targetAdmin));
        when(userService.countAdmins()).thenReturn(1L);

        mockMvc.perform(delete("/api/v1/admin/users/43")
                        .header("X-User-Id", "1")
                        .header("X-User-Role", "ROLE_ADMIN"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Нельзя удалить последнего администратора"));

        verify(userService, never()).deleteUser(anyLong());
    }

    @Test
    @DisplayName("DELETE — удаляет админа если есть другие")
    void shouldDeleteAdminWhenOthersExist() throws Exception {
        when(userService.findUserById(43L)).thenReturn(Optional.of(targetAdmin));
        when(userService.countAdmins()).thenReturn(2L);
        when(orderServiceClient.countActiveOrdersByUserId(43L, "1", "ROLE_ADMIN")).thenReturn(0L);

        mockMvc.perform(delete("/api/v1/admin/users/43")
                        .header("X-User-Id", "1")
                        .header("X-User-Role", "ROLE_ADMIN"))
                .andExpect(status().isNoContent());

        verify(userService).deleteUser(43L);
    }

    @Test
    @DisplayName("DELETE — 409 при наличии активных заказов")
    void shouldReturn409WhenActiveOrdersExist() throws Exception {
        when(userService.findUserById(42L)).thenReturn(Optional.of(targetUser));
        when(orderServiceClient.countActiveOrdersByUserId(42L, "1", "ROLE_ADMIN")).thenReturn(3L);

        mockMvc.perform(delete("/api/v1/admin/users/42")
                        .header("X-User-Id", "1")
                        .header("X-User-Role", "ROLE_ADMIN"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message")
                        .value("Нельзя удалить пользователя с активными заказами (3)"));

        verify(userService, never()).deleteUser(anyLong());
    }

    @Test
    @DisplayName("DELETE — 503 при недоступности order-service")
    void shouldReturn503WhenOrderServiceDown() throws Exception {
        when(userService.findUserById(42L)).thenReturn(Optional.of(targetUser));
        when(orderServiceClient.countActiveOrdersByUserId(anyLong(), anyString(), anyString()))
                .thenThrow(new OrderServiceUnavailableException("Order service unavailable"));

        mockMvc.perform(delete("/api/v1/admin/users/42")
                        .header("X-User-Id", "1")
                        .header("X-User-Role", "ROLE_ADMIN"))
                .andExpect(status().isServiceUnavailable());

        verify(userService, never()).deleteUser(anyLong());
    }
}
