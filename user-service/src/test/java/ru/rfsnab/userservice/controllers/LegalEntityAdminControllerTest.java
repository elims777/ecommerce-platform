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
import ru.rfsnab.userservice.models.LegalEntity;
import ru.rfsnab.userservice.models.enums.VerificationStatus;
import ru.rfsnab.userservice.services.LegalEntityService;
import ru.rfsnab.userservice.services.client.OrderServiceClient;

import static org.mockito.ArgumentMatchers.anyLong;
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
@DisplayName("LegalEntityAdminController DELETE")
class LegalEntityAdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private LegalEntityService legalEntityService;

    @MockitoBean
    private OrderServiceClient orderServiceClient;

    private LegalEntity entityWithInn;
    private LegalEntity entityWithoutInn;

    @BeforeEach
    void setUp() {
        entityWithInn = LegalEntity.builder()
                .id(100L)
                .inn("7707083893")
                .fullName("ООО Ромашка")
                .verificationStatus(VerificationStatus.VERIFIED)
                .build();

        entityWithoutInn = LegalEntity.builder()
                .id(101L)
                .inn(null)
                .fullName("ООО Без ИНН")
                .verificationStatus(VerificationStatus.PENDING)
                .build();
    }

    @Test
    @DisplayName("DELETE — успешно удаляет юрлицо без активных заказов")
    void shouldDeleteLegalEntity() throws Exception {
        when(legalEntityService.getById(100L)).thenReturn(entityWithInn);
        when(orderServiceClient.countActiveOrdersByInn("7707083893", "1", "ROLE_ADMIN")).thenReturn(0L);

        mockMvc.perform(delete("/api/v1/admin/legal-entities/100")
                        .header("X-User-Id", "1")
                        .header("X-User-Role", "ROLE_ADMIN"))
                .andExpect(status().isNoContent());

        verify(legalEntityService).deleteById(100L);
    }

    @Test
    @DisplayName("DELETE — 409 если у юрлица нет ИНН")
    void shouldReturn409WhenInnMissing() throws Exception {
        when(legalEntityService.getById(101L)).thenReturn(entityWithoutInn);

        mockMvc.perform(delete("/api/v1/admin/legal-entities/101")
                        .header("X-User-Id", "1")
                        .header("X-User-Role", "ROLE_ADMIN"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message")
                        .value("Нельзя удалить юрлицо без ИНН — проверка активных заказов невозможна"));

        verify(legalEntityService, never()).deleteById(anyLong());
    }

    @Test
    @DisplayName("DELETE — 409 если есть активные заказы")
    void shouldReturn409WhenActiveOrders() throws Exception {
        when(legalEntityService.getById(100L)).thenReturn(entityWithInn);
        when(orderServiceClient.countActiveOrdersByInn("7707083893", "1", "ROLE_ADMIN")).thenReturn(5L);

        mockMvc.perform(delete("/api/v1/admin/legal-entities/100")
                        .header("X-User-Id", "1")
                        .header("X-User-Role", "ROLE_ADMIN"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message")
                        .value("Нельзя удалить юрлицо с активными заказами (5)"));

        verify(legalEntityService, never()).deleteById(anyLong());
    }
}
