package ru.rfsnab.productservice.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import ru.rfsnab.productservice.exception.BusinessException;
import ru.rfsnab.productservice.exception.CategoryNotFoundException;
import ru.rfsnab.productservice.exception.ProductNotFoundException;
import ru.rfsnab.productservice.model.Category;
import ru.rfsnab.productservice.model.Product;
import ru.rfsnab.productservice.repository.CategoryRepository;
import ru.rfsnab.productservice.repository.ProductRepository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProductService Unit Tests")
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private SlugGeneratorService slugGenerator;

    @InjectMocks
    private ProductService productService;

    private Product testProduct;
    private Category testCategory;

    @BeforeEach
    void setUp() {
        testCategory = Category.builder()
                .id(1L)
                .name("Огнетушители")
                .slug("ognetushiteli")
                .isActive(true)
                .build();

        testProduct = Product.builder()
                .id(1L)
                .name("Огнетушитель ОП-4")
                .slug("ognetushitel-op-4")
                .description("Порошковый огнетушитель")
                .price(new BigDecimal("1500.00"))
                .stockQuantity(100)
                .isActive(true)
                .isFeatured(false)
                .category(testCategory)
                .build();
    }

    // ==================== createProduct() Tests ====================

    @Nested
    @DisplayName("createProduct()")
    class CreateProductTests {

        @Test
        @DisplayName("успешно создаёт продукт с категорией")
        void createProduct_WithCategory_Success() {
            // Given
            Product newProduct = Product.builder()
                    .name("Новый огнетушитель")
                    .category(testCategory)
                    .build();

            when(slugGenerator.generateSlug("Новый огнетушитель")).thenReturn("novyy-ognetushitel");
            when(productRepository.existsBySlug("novyy-ognetushitel")).thenReturn(false);
            when(categoryRepository.findById(1L)).thenReturn(Optional.of(testCategory));
            when(productRepository.save(any(Product.class))).thenAnswer(inv -> {
                Product p = inv.getArgument(0);
                p.setId(2L);
                return p;
            });

            // When
            Product result = productService.createProduct(newProduct);

            // Then
            assertThat(result.getId()).isEqualTo(2L);
            assertThat(result.getSlug()).isEqualTo("novyy-ognetushitel");
            verify(productRepository).save(any(Product.class));
        }

        @Test
        @DisplayName("создаёт уникальный slug при конфликте")
        void createProduct_SlugConflict_GeneratesUniqueSlug() {
            // Given
            Product newProduct = Product.builder()
                    .name("Огнетушитель")
                    .build();

            when(slugGenerator.generateSlug("Огнетушитель")).thenReturn("ognetushitel");
            when(productRepository.existsBySlug("ognetushitel")).thenReturn(true);
            when(slugGenerator.makeUnique("ognetushitel", 2)).thenReturn("ognetushitel-2");
            when(productRepository.existsBySlug("ognetushitel-2")).thenReturn(false);
            when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

            // When
            Product result = productService.createProduct(newProduct);

            // Then
            assertThat(result.getSlug()).isEqualTo("ognetushitel-2");
        }

        @Test
        @DisplayName("выбрасывает исключение для несуществующей категории")
        void createProduct_CategoryNotFound_ThrowsException() {
            // Given
            Category nonExistentCategory = Category.builder().id(999L).build();
            Product newProduct = Product.builder()
                    .name("Товар")
                    .category(nonExistentCategory)
                    .build();

            when(slugGenerator.generateSlug("Товар")).thenReturn("tovar");
            when(productRepository.existsBySlug("tovar")).thenReturn(false);
            when(categoryRepository.findById(999L)).thenReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> productService.createProduct(newProduct))
                    .isInstanceOf(CategoryNotFoundException.class);
        }
    }

    // ==================== getProductById() Tests ====================

    @Nested
    @DisplayName("getProductById()")
    class GetProductByIdTests {

        @Test
        @DisplayName("возвращает продукт по ID")
        void getProductById_Exists_ReturnsProduct() {
            // Given
            when(productRepository.findById(1L)).thenReturn(Optional.of(testProduct));

            // When
            Product result = productService.getProductById(1L);

            // Then
            assertThat(result.getId()).isEqualTo(1L);
            assertThat(result.getName()).isEqualTo("Огнетушитель ОП-4");
        }

        @Test
        @DisplayName("выбрасывает исключение для несуществующего ID")
        void getProductById_NotExists_ThrowsException() {
            // Given
            when(productRepository.findById(999L)).thenReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> productService.getProductById(999L))
                    .isInstanceOf(ProductNotFoundException.class);
        }
    }

    // ==================== Stock Management Tests ====================

    @Nested
    @DisplayName("Stock Management")
    class StockManagementTests {

        @Test
        @DisplayName("increaseStock() увеличивает количество на складе")
        void increaseStock_ValidQuantity_IncreasesStock() {
            // Given
            when(productRepository.findById(1L)).thenReturn(Optional.of(testProduct));
            when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

            // When
            Product result = productService.increaseStock(1L, 50);

            // Then
            assertThat(result.getStockQuantity()).isEqualTo(150); // 100 + 50
        }

        @Test
        @DisplayName("increaseStock() выбрасывает исключение для отрицательного количества")
        void increaseStock_NegativeQuantity_ThrowsException() {
            // When & Then
            assertThatThrownBy(() -> productService.increaseStock(1L, -10))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("положительным");
        }

        @Test
        @DisplayName("decreaseStock() уменьшает количество на складе")
        void decreaseStock_ValidQuantity_DecreasesStock() {
            // Given
            when(productRepository.findById(1L)).thenReturn(Optional.of(testProduct));
            when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

            // When
            Product result = productService.decreaseStock(1L, 30);

            // Then
            assertThat(result.getStockQuantity()).isEqualTo(70); // 100 - 30
        }

        @Test
        @DisplayName("decreaseStock() выбрасывает исключение при недостатке товара")
        void decreaseStock_InsufficientStock_ThrowsException() {
            // Given
            when(productRepository.findById(1L)).thenReturn(Optional.of(testProduct));

            // When & Then
            assertThatThrownBy(() -> productService.decreaseStock(1L, 200))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Недостаточно товара");
        }

        @Test
        @DisplayName("setStock() устанавливает количество на складе")
        void setStock_ValidQuantity_SetsStock() {
            // Given
            when(productRepository.findById(1L)).thenReturn(Optional.of(testProduct));
            when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

            // When
            Product result = productService.setStock(1L, 500);

            // Then
            assertThat(result.getStockQuantity()).isEqualTo(500);
        }

        @Test
        @DisplayName("setStock() выбрасывает исключение для отрицательного количества")
        void setStock_NegativeQuantity_ThrowsException() {
            // When & Then
            assertThatThrownBy(() -> productService.setStock(1L, -1))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("отрицательным");
        }

        @Test
        @DisplayName("checkAvailability() возвращает true при достаточном количестве")
        void checkAvailability_SufficientStock_ReturnsTrue() {
            // Given
            when(productRepository.findById(1L)).thenReturn(Optional.of(testProduct));

            // When
            boolean result = productService.checkAvailability(1L, 50);

            // Then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("checkAvailability() возвращает false при недостаточном количестве")
        void checkAvailability_InsufficientStock_ReturnsFalse() {
            // Given
            when(productRepository.findById(1L)).thenReturn(Optional.of(testProduct));

            // When
            boolean result = productService.checkAvailability(1L, 150);

            // Then
            assertThat(result).isFalse();
        }
    }

    // ==================== Activation Tests ====================

    @Nested
    @DisplayName("Activation")
    class ActivationTests {

        @Test
        @DisplayName("activateProduct() активирует продукт")
        void activateProduct_Success() {
            // Given
            testProduct.setIsActive(false);
            when(productRepository.findById(1L)).thenReturn(Optional.of(testProduct));
            when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

            // When
            Product result = productService.activateProduct(1L);

            // Then
            assertThat(result.getIsActive()).isTrue();
        }

        @Test
        @DisplayName("deactivateProduct() деактивирует продукт")
        void deactivateProduct_Success() {
            // Given
            when(productRepository.findById(1L)).thenReturn(Optional.of(testProduct));
            when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

            // When
            Product result = productService.deactivateProduct(1L);

            // Then
            assertThat(result.getIsActive()).isFalse();
        }
    }

    // ==================== Search & Query Tests ====================

    @Nested
    @DisplayName("Search & Query")
    class SearchQueryTests {

        @Test
        @DisplayName("searchProducts() возвращает результаты поиска")
        void searchProducts_ReturnsResults() {
            // Given
            when(productRepository.searchByName("огне")).thenReturn(List.of(testProduct));

            // When
            List<Product> results = productService.searchProducts("огне");

            // Then
            assertThat(results).hasSize(1);
            assertThat(results.get(0).getName()).contains("Огнетушитель");
        }

        @Test
        @DisplayName("getFeaturedProducts() возвращает рекомендуемые товары")
        void getFeaturedProducts_ReturnsFeatured() {
            // Given
            testProduct.setIsFeatured(true);
            when(productRepository.findFeatured()).thenReturn(List.of(testProduct));

            // When
            List<Product> results = productService.getFeaturedProducts();

            // Then
            assertThat(results).hasSize(1);
        }

        @Test
        @DisplayName("getProductsPage() возвращает страницу продуктов")
        void getProductsPage_ReturnsPage() {
            // Given
            Pageable pageable = PageRequest.of(0, 10);
            Page<Product> page = new PageImpl<>(List.of(testProduct), pageable, 1);
            when(productRepository.findByIsActiveTrue(pageable)).thenReturn(page);

            // When
            Page<Product> result = productService.getProductsPage(pageable);

            // Then
            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getTotalElements()).isEqualTo(1);
        }
    }

    // ==================== deleteProduct() Tests ====================

    @Nested
    @DisplayName("deleteProduct()")
    class DeleteProductTests {

        @Test
        @DisplayName("успешно удаляет существующий продукт")
        void deleteProduct_Exists_DeletesSuccessfully() {
            // Given
            when(productRepository.findById(1L)).thenReturn(Optional.of(testProduct));
            doNothing().when(productRepository).deleteById(1L);

            // When
            productService.deleteProduct(1L);

            // Then
            verify(productRepository).deleteById(1L);
        }

        @Test
        @DisplayName("выбрасывает исключение для несуществующего продукта")
        void deleteProduct_NotExists_ThrowsException() {
            // Given
            when(productRepository.findById(999L)).thenReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> productService.deleteProduct(999L))
                    .isInstanceOf(ProductNotFoundException.class);
        }
    }
}
