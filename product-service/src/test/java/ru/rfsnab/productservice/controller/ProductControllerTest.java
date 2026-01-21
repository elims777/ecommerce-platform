package ru.rfsnab.productservice.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import ru.rfsnab.productservice.dto.ProductRequest;
import ru.rfsnab.productservice.model.Category;
import ru.rfsnab.productservice.model.Product;
import ru.rfsnab.productservice.repository.CategoryRepository;
import ru.rfsnab.productservice.repository.ProductRepository;
import ru.rfsnab.productservice.service.StorageService;

import java.math.BigDecimal;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("ProductController Integration Tests")
class ProductControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private StorageService storageService; // мокаем S3

    private Category testCategory;
    private Product testProduct;

    @BeforeEach
    void setUp() {
        productRepository.deleteAll();
        categoryRepository.deleteAll();

        testCategory = categoryRepository.save(Category.builder()
                .name("Огнетушители")
                .slug("ognetushiteli")
                .isActive(true)
                .displayOrder(1)
                .build());

        testProduct = productRepository.save(Product.builder()
                .name("Огнетушитель ОП-4")
                .slug("ognetushitel-op-4")
                .description("Порошковый огнетушитель")
                .price(new BigDecimal("1500.00"))
                .stockQuantity(100)
                .isActive(true)
                .isFeatured(false)
                .category(testCategory)
                .build());
    }

    // ==================== GET Endpoints (Public) ====================

    @Nested
    @DisplayName("GET /api/v1/products (Public)")
    class GetProductsTests {

        @Test
        @DisplayName("возвращает список активных продуктов без авторизации")
        void getProducts_NoAuth_ReturnsProducts() throws Exception {
            mockMvc.perform(get("/api/v1/products")
                            .param("page", "0")
                            .param("size", "10"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(1)))
                    .andExpect(jsonPath("$.content[0].name", is("Огнетушитель ОП-4")));
        }

        @Test
        @DisplayName("возвращает продукт по ID без авторизации")
        void getProductById_NoAuth_ReturnsProduct() throws Exception {
            mockMvc.perform(get("/api/v1/products/{id}", testProduct.getId()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.name", is("Огнетушитель ОП-4")))
                    .andExpect(jsonPath("$.slug", is("ognetushitel-op-4")));
        }

        @Test
        @DisplayName("возвращает 404 для несуществующего продукта")
        void getProductById_NotFound_Returns404() throws Exception {
            mockMvc.perform(get("/api/v1/products/{id}", 999L))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("возвращает продукт по slug без авторизации")
        void getProductBySlug_NoAuth_ReturnsProduct() throws Exception {
            mockMvc.perform(get("/api/v1/products/slug/{slug}", "ognetushitel-op-4"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.name", is("Огнетушитель ОП-4")));
        }

        @Test
        @DisplayName("поиск продуктов работает без авторизации")
        void searchProducts_NoAuth_ReturnsResults() throws Exception {
            mockMvc.perform(get("/api/v1/products/search")
                            .param("query", "огне"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(1)));
        }
    }

    // ==================== POST Endpoints (Admin/Manager) ====================

    @Nested
    @DisplayName("POST /api/v1/products (Admin/Manager)")
    class CreateProductTests {

        @Test
        @DisplayName("без авторизации → 401 Unauthorized")
        void createProduct_NoAuth_Returns401() throws Exception {
            ProductRequest request = ProductRequest.builder()
                    .name("Новый продукт")
                    .price(new BigDecimal("2000.00"))
                    .build();

            mockMvc.perform(post("/api/v1/products")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @WithMockUser(authorities = "ROLE_USER")
        @DisplayName("с ROLE_USER → 403 Forbidden")
        void createProduct_RoleUser_Returns403() throws Exception {
            ProductRequest request = ProductRequest.builder()
                    .name("Новый продукт")
                    .price(new BigDecimal("2000.00"))
                    .build();

            mockMvc.perform(post("/api/v1/products")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isForbidden());
        }

        @Test
        @WithMockUser(authorities = "ROLE_ADMIN")
        @DisplayName("с ROLE_ADMIN → 200 OK")
        void createProduct_RoleAdmin_Returns200() throws Exception {
            ProductRequest request = ProductRequest.builder()
                    .name("Новый продукт")
                    .description("Описание")
                    .price(new BigDecimal("2000.00"))
                    .stockQuantity(50)
                    .categoryId(testCategory.getId())
                    .build();

            mockMvc.perform(post("/api/v1/products")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.name", is("Новый продукт")))
                    .andExpect(jsonPath("$.slug", notNullValue()));
        }

        @Test
        @WithMockUser(authorities = "ROLE_MANAGER")
        @DisplayName("с ROLE_MANAGER → 200 OK")
        void createProduct_RoleManager_Returns200() throws Exception {
            ProductRequest request = ProductRequest.builder()
                    .name("Продукт менеджера")
                    .price(new BigDecimal("3000.00"))
                    .build();

            mockMvc.perform(post("/api/v1/products")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk());
        }
    }

    // ==================== PUT Endpoints (Admin/Manager) ====================

    @Nested
    @DisplayName("PUT /api/v1/products/{id} (Admin/Manager)")
    class UpdateProductTests {

        @Test
        @DisplayName("без авторизации → 401 Unauthorized")
        void updateProduct_NoAuth_Returns401() throws Exception {
            ProductRequest request = ProductRequest.builder()
                    .name("Обновлённый")
                    .build();

            mockMvc.perform(put("/api/v1/products/{id}", testProduct.getId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @WithMockUser(authorities = "ROLE_ADMIN")
        @DisplayName("с ROLE_ADMIN → 200 OK")
        void updateProduct_RoleAdmin_Returns200() throws Exception {
            ProductRequest request = ProductRequest.builder()
                    .name("Обновлённый огнетушитель")
                    .price(new BigDecimal("1800.00"))
                    .build();

            mockMvc.perform(put("/api/v1/products/{id}", testProduct.getId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.name", is("Обновлённый огнетушитель")));
        }
    }

    // ==================== DELETE Endpoints (Admin/Manager) ====================

    @Nested
    @DisplayName("DELETE /api/v1/products/{id} (Admin/Manager)")
    class DeleteProductTests {

        @Test
        @DisplayName("без авторизации → 401 Unauthorized")
        void deleteProduct_NoAuth_Returns401() throws Exception {
            mockMvc.perform(delete("/api/v1/products/{id}", testProduct.getId()))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @WithMockUser(authorities = "ROLE_USER")
        @DisplayName("с ROLE_USER → 403 Forbidden")
        void deleteProduct_RoleUser_Returns403() throws Exception {
            mockMvc.perform(delete("/api/v1/products/{id}", testProduct.getId()))
                    .andExpect(status().isForbidden());
        }

        @Test
        @WithMockUser(authorities = "ROLE_ADMIN")
        @DisplayName("с ROLE_ADMIN → 204 No Content")
        void deleteProduct_RoleAdmin_Returns204() throws Exception {
            mockMvc.perform(delete("/api/v1/products/{id}", testProduct.getId()))
                    .andExpect(status().isNoContent());
        }
    }

    // ==================== Stock Management ====================

    @Nested
    @DisplayName("PUT /api/v1/products/{id}/stock")
    class StockManagementTests {

        @Test
        @WithMockUser(authorities = "ROLE_ADMIN")
        @DisplayName("изменение количества на складе → 200 OK")
        void changeStock_RoleAdmin_Returns200() throws Exception {
            mockMvc.perform(put("/api/v1/products/{id}/stock", testProduct.getId())
                            .param("quantity", "200"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.stockQuantity", is(200)));
        }

        @Test
        @DisplayName("без авторизации → 401 Unauthorized")
        void changeStock_NoAuth_Returns401() throws Exception {
            mockMvc.perform(put("/api/v1/products/{id}/stock", testProduct.getId())
                            .param("quantity", "200"))
                    .andExpect(status().isUnauthorized());
        }
    }
}
