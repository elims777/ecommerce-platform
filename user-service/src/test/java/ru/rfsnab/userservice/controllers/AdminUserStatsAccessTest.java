package ru.rfsnab.userservice.controllers;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Доступ к сводке по пользователям (GET /api/v1/admin/users/stats).
 * В отличие от AdminUserControllerTest (TestSecurityConfig без method security,
 * нужен для проверки бизнес-логики с замоканным UserService) здесь поднимается
 * MethodSecurityTestConfig, чтобы реально проверить @PreAuthorize на контроллере.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(MethodSecurityTestConfig.class)
@ActiveProfiles("test")
@DisplayName("Доступ к сводке по пользователям для админки")
class AdminUserStatsAccessTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("аноним не получает сводку по пользователям")
    void anonymousIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users/stats")).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(authorities = "ROLE_USER")
    @DisplayName("обычному пользователю сводка запрещена")
    void plainUserIsForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users/stats")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "ROLE_ADMIN")
    @DisplayName("ADMIN получает сводку")
    void adminIsAllowed() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users/stats")).andExpect(status().isOk());
    }
}
