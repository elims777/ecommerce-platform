package ru.rfsnab.productservice.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Админские чтения каталога закрыты, публичные — открыты.
 * Правила стоят ДО общего permitAll на GET /api/v1/products/**: если их
 * переставить ниже, permitAll перехватит и эти тесты покраснеют.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Доступ к админским чтениям каталога")
class ProductAdminAccessTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("аноним не получает список товаров админки")
    void anonymousIsRejectedFromAdminList() throws Exception {
        mockMvc.perform(get("/api/v1/products/admin")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("аноним не получает сводку по каталогу")
    void anonymousIsRejectedFromStats() throws Exception {
        mockMvc.perform(get("/api/v1/products/stats")).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(authorities = "ROLE_USER")
    @DisplayName("обычному пользователю админские чтения запрещены")
    void plainUserIsForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/products/admin")).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/products/stats")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "ROLE_ADMIN")
    @DisplayName("ADMIN проходит на оба эндпоинта")
    void adminIsAllowed() throws Exception {
        mockMvc.perform(get("/api/v1/products/admin")).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/products/stats")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = "ROLE_MANAGER")
    @DisplayName("MANAGER проходит на оба эндпоинта")
    void managerIsAllowed() throws Exception {
        mockMvc.perform(get("/api/v1/products/admin")).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/products/stats")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("публичный каталог анониму по-прежнему открыт")
    void publicCatalogStaysOpen() throws Exception {
        mockMvc.perform(get("/api/v1/products")).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/products/count-available")).andExpect(status().isOk());
    }
}
