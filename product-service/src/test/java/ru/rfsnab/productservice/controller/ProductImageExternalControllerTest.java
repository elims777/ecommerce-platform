package ru.rfsnab.productservice.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import ru.rfsnab.productservice.model.Category;
import ru.rfsnab.productservice.model.Product;
import ru.rfsnab.productservice.repository.CategoryRepository;
import ru.rfsnab.productservice.repository.ProductRepository;
import ru.rfsnab.productservice.service.StorageService;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("ProductImageExternalController Integration Tests")
class ProductImageExternalControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @MockitoBean
    private StorageService storageService;

    private Product testProduct;

    @BeforeEach
    void setUp() {
        productRepository.deleteAll();
        categoryRepository.deleteAll();

        Category category = Category.builder()
                .name("Тестовая категория")
                .slug("test-category")
                .isActive(true)
                .build();
        categoryRepository.save(category);

        testProduct = Product.builder()
                .name("Тестовый товар")
                .slug("test-product")
                .description("Описание")
                .price(BigDecimal.valueOf(1000))
                .stockQuantity(10)
                .category(category)
                .isActive(true)
                .externalId("ext-123")
                .build();
        productRepository.save(testProduct);
    }

    @Test
    @WithMockUser
    @DisplayName("POST /api/v1/products/external/{externalId}/images - успешная загрузка")
    void uploadImageByExternalId_Success() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test.jpg",
                MediaType.IMAGE_JPEG_VALUE,
                "test image content".getBytes()
        );

        when(storageService.uploadFile(any(), any())).thenReturn("https://storage.yandexcloud.net/test.webp");

        mockMvc.perform(multipart("/api/v1/products/external/{externalId}/images", "ext-123")
                        .file(file)
                        .header("X-Internal-Token", "test-secret"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fileUrl").exists());
    }

    @Test
    @WithMockUser
    @DisplayName("POST /api/v1/products/external/{externalId}/images - товар не найден")
    void uploadImageByExternalId_ProductNotFound() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test.jpg",
                MediaType.IMAGE_JPEG_VALUE,
                "test image content".getBytes()
        );

        mockMvc.perform(multipart("/api/v1/products/external/{externalId}/images", "non-existent")
                        .file(file)
                        .header("X-Internal-Token", "test-secret"))
                .andExpect(status().isNotFound());
    }
}
