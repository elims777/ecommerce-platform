package ru.rfsnab.productservice.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import ru.rfsnab.productservice.dto.ProductStockResponse;
import ru.rfsnab.productservice.exception.BusinessException;
import ru.rfsnab.productservice.model.Product;
import ru.rfsnab.productservice.service.ProductMergeService;
import ru.rfsnab.productservice.service.ProductStockService;
import ru.rfsnab.productservice.service.StorageService;

import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("ProductMergeController")
class ProductMergeControllerTest {

    private static final long MAIN_ID = 10L;
    private static final long ONE_C_ID = 2L;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProductMergeService mergeService;

    @MockitoBean
    private ProductStockService stockService;

    @MockitoBean
    private StorageService storageService;

    @Test
    @WithMockUser(authorities = "ROLE_ADMIN")
    @DisplayName("POST /admin/{id}/merged/{oneCId} → 204 и вызов merge")
    void merge_Returns204() throws Exception {
        mockMvc.perform(post("/api/v1/products/admin/{id}/merged/{oneCId}", MAIN_ID, ONE_C_ID))
                .andExpect(status().isNoContent());

        verify(mergeService).merge(MAIN_ID, ONE_C_ID);
    }

    @Test
    @WithMockUser(authorities = "ROLE_ADMIN")
    @DisplayName("POST /admin/{id}/merged/{oneCId}: BusinessException → 400")
    void merge_BusinessException_Returns400() throws Exception {
        doThrow(new BusinessException("Главным может быть только товар Факела"))
                .when(mergeService).merge(MAIN_ID, ONE_C_ID);

        mockMvc.perform(post("/api/v1/products/admin/{id}/merged/{oneCId}", MAIN_ID, ONE_C_ID))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("Главным может быть только товар Факела")));
    }

    @Test
    @WithMockUser(authorities = "ROLE_ADMIN")
    @DisplayName("DELETE /admin/merged/{oneCId} → 204 и вызов unmerge")
    void unmerge_Returns204() throws Exception {
        mockMvc.perform(delete("/api/v1/products/admin/merged/{oneCId}", ONE_C_ID))
                .andExpect(status().isNoContent());

        verify(mergeService).unmerge(ONE_C_ID);
    }

    @Test
    @WithMockUser(authorities = "ROLE_USER")
    @DisplayName("POST склейки с ROLE_USER → 403")
    void merge_RoleUser_Returns403() throws Exception {
        mockMvc.perform(post("/api/v1/products/admin/{id}/merged/{oneCId}", MAIN_ID, ONE_C_ID))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "ROLE_USER")
    @DisplayName("GET /admin/{id}/merged с ROLE_USER → 403")
    void getMerged_RoleUser_Returns403() throws Exception {
        mockMvc.perform(get("/api/v1/products/admin/{id}/merged", MAIN_ID))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "ROLE_ADMIN")
    @DisplayName("GET /admin/{id}/merged возвращает склеенные товары с их собственными остатками")
    void getMerged_ReturnsListWithOwnStocks() throws Exception {
        Product oneC = Product.builder().id(ONE_C_ID).name("Перчатки 1С").slug("perchatki-1c")
                .source("INTERNAL").externalId("uuid-1c").sku("5004").mergedIntoProductId(MAIN_ID).build();
        when(mergeService.getMerged(MAIN_ID)).thenReturn(List.of(oneC));
        when(stockService.getOwnStocks(anyCollection())).thenReturn(Map.of(ONE_C_ID,
                List.of(new ProductStockResponse(1L, "SYKTYVKAR", "Основной склад", "Сыктывкар", 50))));

        mockMvc.perform(get("/api/v1/products/admin/{id}/merged", MAIN_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].externalId", is("uuid-1c")))
                .andExpect(jsonPath("$[0].stocks[0].quantity", is(50)));
    }

    @Test
    @WithMockUser(authorities = "ROLE_ADMIN")
    @DisplayName("GET /admin/{id}/stocks возвращает собственные остатки записи")
    void getOwnStocks_ReturnsList() throws Exception {
        when(stockService.getOwnStocks(anyCollection())).thenReturn(Map.of(MAIN_ID,
                List.of(new ProductStockResponse(2L, "PUSHKIN", "Склад Пушкин", "Пушкин", 0))));

        mockMvc.perform(get("/api/v1/products/admin/{id}/stocks", MAIN_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].code", is("PUSHKIN")))
                .andExpect(jsonPath("$[0].quantity", is(0)));
    }

    @Test
    @WithMockUser(authorities = "ROLE_ADMIN")
    @DisplayName("GET /admin/merge-candidates возвращает страницу кандидатов")
    void getMergeCandidates_ReturnsPage() throws Exception {
        Product oneC = Product.builder().id(ONE_C_ID).name("Перчатки 1С").slug("perchatki-1c")
                .source("INTERNAL").externalId("uuid-1c").build();
        when(mergeService.findCandidates(eq("перч"), any())).thenReturn(new PageImpl<>(List.of(oneC), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/v1/products/admin/merge-candidates").param("query", "перч"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].name", is("Перчатки 1С")));
    }
}
