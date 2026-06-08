package ru.rfsnab.productservice.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import ru.rfsnab.productservice.dto.BatchProductImportRequest;
import ru.rfsnab.productservice.dto.ProductImportItem;
import ru.rfsnab.productservice.model.Category;
import ru.rfsnab.productservice.model.Product;
import ru.rfsnab.productservice.repository.CategoryRepository;
import ru.rfsnab.productservice.repository.ProductRepository;
import ru.rfsnab.productservice.service.StorageService;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("ProductImportController")
class ProductImportControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private ProductRepository productRepository;
    @Autowired private CategoryRepository categoryRepository;

    @MockitoBean private StorageService storageService;

    private Category importCategory;

    @BeforeEach
    void setUp() {
        productRepository.deleteAll();
        categoryRepository.deleteAll();

        importCategory = categoryRepository.save(Category.builder()
                .name("import-1c")
                .slug("import-1c")
                .isActive(true)
                .displayOrder(0)
                .build());
    }

    @Test
    @WithMockUser
    @DisplayName("POST /import/batch — создаёт товар с description и material")
    void shouldImportProductWithDescriptionAndMaterial() throws Exception {
        BatchProductImportRequest request = new BatchProductImportRequest(List.of(
                ProductImportItem.builder()
                        .externalId("FTK-87490974")
                        .name("Костюм летний")
                        .description("Полное описание костюма")
                        .material("Ткань Твил 65% хлопок")
                        .price(new BigDecimal("9267"))
                        .wholesalePrice(new BigDecimal("9267"))
                        .source("FTK")
                        .build()
        ));

        mockMvc.perform(post("/api/v1/products/import/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(1))
                .andExpect(jsonPath("$.failed").value(0));

        Product saved = productRepository.findAll().stream()
                .filter(p -> "FTK-87490974".equals(p.getExternalId()))
                .findFirst()
                .orElseThrow();

        assertThat(saved.getDescription()).isEqualTo("Полное описание костюма");
        assertThat(saved.getMaterial()).isEqualTo("Ткань Твил 65% хлопок");
        assertThat(saved.getSource()).isEqualTo("FTK");
    }

    @Test
    @WithMockUser
    @DisplayName("POST /import/batch — повторный импорт не затирает description если null")
    void shouldNotOverwriteDescriptionOnReimport() throws Exception {
        // Первый импорт — с description
        BatchProductImportRequest first = new BatchProductImportRequest(List.of(
                ProductImportItem.builder()
                        .externalId("FTK-111")
                        .name("Товар")
                        .description("Описание от ФТК")
                        .material("Хлопок")
                        .price(BigDecimal.ONE)
                        .wholesalePrice(BigDecimal.ONE)
                        .source("FTK")
                        .build()
        ));

        mockMvc.perform(post("/api/v1/products/import/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(first)))
                .andExpect(status().isOk());

        // Второй импорт — description=null (обновление цены из 1С)
        BatchProductImportRequest second = new BatchProductImportRequest(List.of(
                ProductImportItem.builder()
                        .externalId("FTK-111")
                        .name("Товар")
                        .price(new BigDecimal("2000"))
                        .wholesalePrice(new BigDecimal("2000"))
                        .source("FTK")
                        .build()
        ));

        mockMvc.perform(post("/api/v1/products/import/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(second)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.updated").value(1));

        Product saved = productRepository.findAll().stream()
                .filter(p -> "FTK-111".equals(p.getExternalId()))
                .findFirst()
                .orElseThrow();

        assertThat(saved.getDescription()).isEqualTo("Описание от ФТК");
        assertThat(saved.getMaterial()).isEqualTo("Хлопок");
        assertThat(saved.getPrice()).isEqualByComparingTo("2000");
    }

    @Test
    @WithMockUser
    @DisplayName("POST /import/batch — shortDescription и description независимы")
    void shouldStoreShortDescriptionAndDescriptionIndependently() throws Exception {
        BatchProductImportRequest request = new BatchProductImportRequest(List.of(
                ProductImportItem.builder()
                        .externalId("FTK-222")
                        .name("Жилет")
                        .description("Полное описание жилета")
                        .shortDescription("Краткое из 1С")
                        .price(new BigDecimal("4660"))
                        .wholesalePrice(new BigDecimal("4660"))
                        .source("FTK")
                        .build()
        ));

        mockMvc.perform(post("/api/v1/products/import/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        Product saved = productRepository.findAll().stream()
                .filter(p -> "FTK-222".equals(p.getExternalId()))
                .findFirst()
                .orElseThrow();

        assertThat(saved.getDescription()).isEqualTo("Полное описание жилета");
        assertThat(saved.getShortDescription()).isEqualTo("Краткое из 1С");
    }

    @Test
    @WithMockUser
    @DisplayName("POST /import/batch — пустой список возвращает 400 (валидация @NotEmpty)")
    void shouldRejectEmptyBatch() throws Exception {
        BatchProductImportRequest request = new BatchProductImportRequest(List.of());

        mockMvc.perform(post("/api/v1/products/import/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }
}
