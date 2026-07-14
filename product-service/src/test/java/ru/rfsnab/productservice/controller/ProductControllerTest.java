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
import ru.rfsnab.productservice.service.CategoryService;
import ru.rfsnab.productservice.service.StorageService;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
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
    private CategoryService categoryService;

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
        @DisplayName("возвращает children[] вместо variants[] для товара с дочерними вариантами")
        void getProductById_WithChildren_ReturnsChildren() throws Exception {
            Product child = productRepository.save(Product.builder()
                    .name("Огнетушитель ОП-4 (S)")
                    .slug("ognetushitel-op-4-s")
                    .price(new BigDecimal("1500.00"))
                    .stockQuantity(10)
                    .isActive(true)
                    .isFeatured(false)
                    .isVariantChild(true)
                    .parentProductId(testProduct.getId())
                    .category(testCategory)
                    .build());

            mockMvc.perform(get("/api/v1/products/{id}", testProduct.getId()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.children", hasSize(1)))
                    .andExpect(jsonPath("$.children[0].name", is("Огнетушитель ОП-4 (S)")))
                    .andExpect(jsonPath("$.children[0].isVariantChild", is(true)));
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
        @DisplayName("поиск продуктов работает без авторизации и возвращает страницу")
        void searchProducts_NoAuth_ReturnsResults() throws Exception {
            mockMvc.perform(get("/api/v1/products/search")
                            .param("query", "огне"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(1)))
                    .andExpect(jsonPath("$.content[0].name", is("Огнетушитель ОП-4")))
                    .andExpect(jsonPath("$.totalElements", is(1)))
                    .andExpect(jsonPath("$.totalPages", is(1)));
        }

        @Test
        @DisplayName("поиск без совпадений возвращает пустую страницу")
        void searchProducts_NoMatches_ReturnsEmptyPage() throws Exception {
            mockMvc.perform(get("/api/v1/products/search")
                            .param("query", "несуществующий-товар"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(0)))
                    .andExpect(jsonPath("$.totalElements", is(0)));
        }

        @Test
        @DisplayName("поиск возвращает вторую страницу результатов")
        void searchProducts_SecondPage_ReturnsRemainingResults() throws Exception {
            productRepository.save(Product.builder()
                    .name("Огнетушитель ОП-8")
                    .slug("ognetushitel-op-8")
                    .price(new BigDecimal("2000.00"))
                    .stockQuantity(50)
                    .isActive(true)
                    .isFeatured(false)
                    .category(testCategory)
                    .build());

            mockMvc.perform(get("/api/v1/products/search")
                            .param("query", "огне")
                            .param("page", "1")
                            .param("size", "1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(1)))
                    .andExpect(jsonPath("$.totalElements", is(2)))
                    .andExpect(jsonPath("$.totalPages", is(2)));
        }
    }

    // ==================== GET /category/{id} (поддерево категорий) ====================

    @Nested
    @DisplayName("GET /api/v1/products/category/{id} (поддерево категорий)")
    class GetProductsByCategoryTests {

        @Test
        @DisplayName("для листовой категории возвращает её товары")
        void getProductsByCategory_LeafCategory_ReturnsOwnProducts() throws Exception {
            mockMvc.perform(get("/api/v1/products/category/{categoryId}", testCategory.getId()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(1)))
                    .andExpect(jsonPath("$.content[0].name", is("Огнетушитель ОП-4")));
        }

        @Test
        @DisplayName("для родительской категории возвращает товары всех подкатегорий")
        void getProductsByCategory_ParentCategory_ReturnsSubtreeProducts() throws Exception {
            Category parentCategory = categoryRepository.save(Category.builder()
                    .name("Спецодежда и СИЗ")
                    .slug("specodezhda-i-siz")
                    .isActive(true)
                    .displayOrder(1)
                    .build());

            testCategory.setParent(parentCategory);
            categoryRepository.save(testCategory);

            Category siblingLeaf = categoryRepository.save(Category.builder()
                    .name("Перчатки")
                    .slug("perchatki")
                    .isActive(true)
                    .displayOrder(2)
                    .parent(parentCategory)
                    .build());

            productRepository.save(Product.builder()
                    .name("Перчатки х/б")
                    .slug("perchatki-hb")
                    .price(new BigDecimal("50.00"))
                    .stockQuantity(500)
                    .isActive(true)
                    .isFeatured(false)
                    .category(siblingLeaf)
                    .build());

            categoryService.refreshCategoryTree();

            mockMvc.perform(get("/api/v1/products/category/{categoryId}", parentCategory.getId()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(2)))
                    .andExpect(jsonPath("$.totalElements", is(2)));
        }

        @Test
        @DisplayName("возвращает 404 для несуществующей категории")
        void getProductsByCategory_NotFound_Returns404() throws Exception {
            mockMvc.perform(get("/api/v1/products/category/{categoryId}", 999L))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("по умолчанию сортирует по displayOrder (порядок из админки)")
        void getProductsByCategory_DefaultSort_OrdersByDisplayOrder() throws Exception {
            // testProduct имеет displayOrder=0 (дефолт)
            productRepository.save(Product.builder()
                    .name("Огнетушитель ОП-8")
                    .slug("ognetushitel-op-8")
                    .price(new BigDecimal("2000.00"))
                    .stockQuantity(50)
                    .isActive(true)
                    .isFeatured(false)
                    .displayOrder(10)
                    .category(testCategory)
                    .build());

            productRepository.save(Product.builder()
                    .name("Огнетушитель ОУ-2")
                    .slug("ognetushitel-ou-2")
                    .price(new BigDecimal("3000.00"))
                    .stockQuantity(30)
                    .isActive(true)
                    .isFeatured(false)
                    .displayOrder(5)
                    .category(testCategory)
                    .build());

            mockMvc.perform(get("/api/v1/products/category/{categoryId}", testCategory.getId()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(3)))
                    .andExpect(jsonPath("$.content[0].name", is("Огнетушитель ОП-4")))
                    .andExpect(jsonPath("$.content[1].name", is("Огнетушитель ОУ-2")))
                    .andExpect(jsonPath("$.content[2].name", is("Огнетушитель ОП-8")));
        }
    }

    // ==================== GET /featured ====================

    @Nested
    @DisplayName("GET /api/v1/products/featured (Public)")
    class GetFeaturedProductsTests {

        @Test
        @DisplayName("возвращает только featured товары")
        void getFeatured_ReturnsOnlyFeatured() throws Exception {
            Product featured = productRepository.save(Product.builder()
                    .name("Огнетушитель ОП-8 (хит)")
                    .slug("ognetushitel-op-8-hit")
                    .price(new BigDecimal("2000.00"))
                    .stockQuantity(50)
                    .isActive(true)
                    .isFeatured(true)
                    .category(testCategory)
                    .build());

            mockMvc.perform(get("/api/v1/products/featured"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(1)))
                    .andExpect(jsonPath("$.content[0].name", is(featured.getName())));
        }

        @Test
        @DisplayName("без авторизации → 200 OK")
        void getFeatured_NoAuth_Returns200() throws Exception {
            mockMvc.perform(get("/api/v1/products/featured"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("не включает дочерние товары-варианты")
        void getFeatured_ExcludesVariantChildren() throws Exception {
            Product parent = productRepository.save(Product.builder()
                    .name("Куртка утеплённая (хит)")
                    .slug("kurtka-utheplennaya-hit")
                    .price(new BigDecimal("3000.00"))
                    .stockQuantity(10)
                    .isActive(true)
                    .isFeatured(true)
                    .category(testCategory)
                    .build());

            productRepository.save(Product.builder()
                    .name("Куртка утеплённая (хит) (48)")
                    .slug("kurtka-utheplennaya-hit-48")
                    .price(new BigDecimal("3000.00"))
                    .stockQuantity(5)
                    .isActive(true)
                    .isFeatured(true)
                    .isVariantChild(true)
                    .parentProductId(parent.getId())
                    .category(testCategory)
                    .build());

            mockMvc.perform(get("/api/v1/products/featured"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(1)))
                    .andExpect(jsonPath("$.content[0].name", is(parent.getName())));
        }

        @Test
        @DisplayName("не включает неактивные товары")
        void getFeatured_ExcludesInactive() throws Exception {
            productRepository.save(Product.builder()
                    .name("Неактивный хит")
                    .slug("neaktivnyj-hit")
                    .price(new BigDecimal("1000.00"))
                    .stockQuantity(10)
                    .isActive(false)
                    .isFeatured(true)
                    .category(testCategory)
                    .build());

            mockMvc.perform(get("/api/v1/products/featured"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(0)));
        }

        @Test
        @DisplayName("отмечает hasVariants=true при наличии активного дочернего варианта")
        void getFeatured_MarksHasVariants() throws Exception {
            Product parent = productRepository.save(Product.builder()
                    .name("Куртка утеплённая (хит)")
                    .slug("kurtka-utheplennaya-hit")
                    .price(new BigDecimal("3000.00"))
                    .stockQuantity(10)
                    .isActive(true)
                    .isFeatured(true)
                    .category(testCategory)
                    .build());

            productRepository.save(Product.builder()
                    .name("Куртка утеплённая (хит) (48)")
                    .slug("kurtka-utheplennaya-hit-48")
                    .price(new BigDecimal("3000.00"))
                    .stockQuantity(5)
                    .isActive(true)
                    .isFeatured(false)
                    .isVariantChild(true)
                    .parentProductId(parent.getId())
                    .category(testCategory)
                    .build());

            mockMvc.perform(get("/api/v1/products/featured"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(1)))
                    .andExpect(jsonPath("$.content[0].hasVariants", is(true)));
        }

        @Test
        @DisplayName("нет featured товаров → пустая страница")
        void getFeatured_Empty_ReturnsEmptyPage() throws Exception {
            mockMvc.perform(get("/api/v1/products/featured"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(0)));
        }
    }

    // ==================== GET /count-available ====================

    @Nested
    @DisplayName("GET /api/v1/products/count-available (Public)")
    class CountAvailableProductsTests {

        @Test
        @DisplayName("без авторизации возвращает количество товаров в наличии")
        void countAvailableProducts_NoAuth_ReturnsCount() throws Exception {
            mockMvc.perform(get("/api/v1/products/count-available"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.count", is(1)));
        }

        @Test
        @DisplayName("не учитывает товары без остатка")
        void countAvailableProducts_ZeroStock_ExcludedFromCount() throws Exception {
            productRepository.save(Product.builder()
                    .name("Товар без остатка")
                    .slug("tovar-bez-ostatka")
                    .price(new BigDecimal("100.00"))
                    .stockQuantity(0)
                    .isActive(true)
                    .isFeatured(false)
                    .category(testCategory)
                    .build());

            mockMvc.perform(get("/api/v1/products/count-available"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.count", is(1)));
        }

        @Test
        @DisplayName("учитывает остаток дочерних вариантов у родителя с нулевым собственным остатком")
        void countAvailableProducts_ParentWithVariantStock_CountsParentOnce() throws Exception {
            Product ftkParent = productRepository.save(Product.builder()
                    .name("Куртка утеплённая")
                    .slug("kurtka-utheplennaya")
                    .price(new BigDecimal("3000.00"))
                    .stockQuantity(0)
                    .isActive(true)
                    .isFeatured(false)
                    .category(testCategory)
                    .build());

            productRepository.save(Product.builder()
                    .name("Куртка утеплённая (48)")
                    .slug("kurtka-utheplennaya-48")
                    .price(new BigDecimal("3000.00"))
                    .stockQuantity(10)
                    .isActive(true)
                    .isFeatured(false)
                    .isVariantChild(true)
                    .parentProductId(ftkParent.getId())
                    .category(testCategory)
                    .build());

            mockMvc.perform(get("/api/v1/products/count-available"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.count", is(2))); // testProduct + ftkParent (через дочерний остаток)
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

    // ==================== Batch Delete ====================

    @Nested
    @DisplayName("DELETE /api/v1/products/batch (Admin)")
    class BatchDeleteTests {

        @Test
        @DisplayName("без авторизации → 401")
        void batchDelete_NoAuth_Returns401() throws Exception {
            mockMvc.perform(delete("/api/v1/products/batch")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("[1,2,3]"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @WithMockUser(authorities = "ROLE_USER")
        @DisplayName("с ROLE_USER → 403")
        void batchDelete_RoleUser_Returns403() throws Exception {
            mockMvc.perform(delete("/api/v1/products/batch")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("[1,2,3]"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @WithMockUser(authorities = "ROLE_ADMIN")
        @DisplayName("с ROLE_ADMIN, существующие ID → 204 No Content")
        void batchDelete_RoleAdmin_Returns204() throws Exception {
            Long id1 = testProduct.getId();
            Product second = productRepository.save(Product.builder()
                    .name("Второй товар")
                    .slug("vtoroy-tovar")
                    .price(new BigDecimal("500.00"))
                    .stockQuantity(10)
                    .isActive(true)
                    .isFeatured(false)
                    .category(testCategory)
                    .build());

            mockMvc.perform(delete("/api/v1/products/batch")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("[%d,%d]".formatted(id1, second.getId())))
                    .andExpect(status().isNoContent());

            assertThat(productRepository.findById(id1)).isEmpty();
            assertThat(productRepository.findById(second.getId())).isEmpty();
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
