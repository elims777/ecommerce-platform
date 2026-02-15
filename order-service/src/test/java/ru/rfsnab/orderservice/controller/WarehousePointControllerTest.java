package ru.rfsnab.orderservice.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import ru.rfsnab.orderservice.BaseIntegrationTest;
import ru.rfsnab.orderservice.models.entity.WarehousePoint;
import ru.rfsnab.orderservice.service.WarehousePointService;

import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@AutoConfigureMockMvc
@DisplayName("WarehousePointController")
class WarehousePointControllerTest extends BaseIntegrationTest {

    @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private WarehousePointService warehousePointService;

    @Test
    @DisplayName("GET /api/v1/warehouse-points — возвращает список активных точек")
    void shouldReturnActivePoints() throws Exception {
        WarehousePoint point = WarehousePoint.builder()
                .id(1L).name("Склад РФСнаб")
                .city("Сыктывкар").street("Октябрьский проспект")
                .building("1").postalCode("167000")
                .phoneNumber("+7 (8212) 00-00-00")
                .workingHours("Пн-Пт: 9:00-18:00")
                .description("Основной склад")
                .active(true).build();

        when(warehousePointService.getActivePoints()).thenReturn(List.of(point));

        mockMvc.perform(get("/api/v1/warehouse-points")
                        .with(user("100")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name").value("Склад РФСнаб"))
                .andExpect(jsonPath("$[0].city").value("Сыктывкар"))
                .andExpect(jsonPath("$[0].active").value(true));
    }

    @Test
    @DisplayName("GET /api/v1/warehouse-points — пустой список")
    void shouldReturnEmptyList() throws Exception {
        when(warehousePointService.getActivePoints()).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/warehouse-points")
                        .with(user("100")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }
}