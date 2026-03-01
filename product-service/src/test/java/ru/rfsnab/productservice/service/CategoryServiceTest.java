package ru.rfsnab.productservice.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.rfsnab.productservice.dto.CategoryTreeDTO;
import ru.rfsnab.productservice.exception.BusinessException;
import ru.rfsnab.productservice.exception.CategoryNotFoundException;
import ru.rfsnab.productservice.model.Category;
import ru.rfsnab.productservice.repository.CategoryRepository;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CategoryService Unit Tests")
class CategoryServiceTest {

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private ProductService productService;

    @Mock
    private SlugGeneratorService slugGenerator;

    private CategoryService categoryService;

    private Category rootCategory;
    private Category childCategory;

    @BeforeEach
    void setUp() {
        categoryService = new CategoryService(categoryRepository, productService, slugGenerator);

        rootCategory = Category.builder()
                .id(1L)
                .name("Огнетушители")
                .slug("ognetushiteli")
                .isActive(true)
                .displayOrder(1)
                .parent(null)
                .build();

        childCategory = Category.builder()
                .id(2L)
                .name("Порошковые")
                .slug("poroshkovye")
                .isActive(true)
                .displayOrder(1)
                .parent(rootCategory)
                .build();
    }

    // ==================== createCategory() Tests ====================

    @Nested
    @DisplayName("createCategory()")
    class CreateCategoryTests {

        @Test
        @DisplayName("успешно создаёт категорию без родителя")
        void createCategory_WithoutParent_Success() {
            // Given
            Category newCategory = Category.builder()
                    .name("Новая категория")
                    .build();

            when(slugGenerator.generateSlug("Новая категория")).thenReturn("novaya-kategoriya");
            when(categoryRepository.existsBySlug("novaya-kategoriya")).thenReturn(false);
            when(categoryRepository.save(any(Category.class))).thenAnswer(inv -> {
                Category c = inv.getArgument(0);
                c.setId(3L);
                return c;
            });
            when(categoryRepository.findAll()).thenReturn(List.of());

            // When
            Category result = categoryService.createCategory(newCategory);

            // Then
            assertThat(result.getId()).isEqualTo(3L);
            assertThat(result.getSlug()).isEqualTo("novaya-kategoriya");
            assertThat(result.getIsActive()).isTrue(); // default
            assertThat(result.getDisplayOrder()).isEqualTo(0); // default
        }

        @Test
        @DisplayName("успешно создаёт категорию с родителем")
        void createCategory_WithParent_Success() {
            // Given
            Category newCategory = Category.builder()
                    .name("Подкатегория")
                    .parent(rootCategory)
                    .build();

            when(slugGenerator.generateSlug("Подкатегория")).thenReturn("podkategoriya");
            when(categoryRepository.existsBySlug("podkategoriya")).thenReturn(false);
            when(categoryRepository.findById(1L)).thenReturn(Optional.of(rootCategory));
            when(categoryRepository.save(any(Category.class))).thenAnswer(inv -> {
                Category c = inv.getArgument(0);
                c.setId(3L);
                return c;
            });
            when(categoryRepository.findAll()).thenReturn(List.of());

            // When
            Category result = categoryService.createCategory(newCategory);

            // Then
            assertThat(result.getParent()).isEqualTo(rootCategory);
        }

        @Test
        @DisplayName("генерирует уникальный slug при конфликте")
        void createCategory_SlugConflict_GeneratesUniqueSlug() {
            // Given
            Category newCategory = Category.builder()
                    .name("Огнетушители")
                    .build();

            when(slugGenerator.generateSlug("Огнетушители")).thenReturn("ognetushiteli");
            when(categoryRepository.existsBySlug("ognetushiteli")).thenReturn(true);
            when(slugGenerator.makeUnique("ognetushiteli", 2)).thenReturn("ognetushiteli-2");
            when(categoryRepository.existsBySlug("ognetushiteli-2")).thenReturn(false);
            when(categoryRepository.save(any(Category.class))).thenAnswer(inv -> inv.getArgument(0));
            when(categoryRepository.findAll()).thenReturn(List.of());

            // When
            Category result = categoryService.createCategory(newCategory);

            // Then
            assertThat(result.getSlug()).isEqualTo("ognetushiteli-2");
        }
    }

    // ==================== updateCategory() Tests ====================

    @Nested
    @DisplayName("updateCategory()")
    class UpdateCategoryTests {

        @Test
        @DisplayName("успешно обновляет категорию")
        void updateCategory_ValidData_Success() {
            // Given
            Category updatedData = Category.builder()
                    .name("Обновлённое название")
                    .description("Новое описание")
                    .isActive(true)
                    .displayOrder(5)
                    .build();

            when(categoryRepository.findById(1L)).thenReturn(Optional.of(rootCategory));
            when(categoryRepository.save(any(Category.class))).thenAnswer(inv -> inv.getArgument(0));
            when(categoryRepository.findAll()).thenReturn(List.of());

            // When
            Category result = categoryService.updateCategory(1L, updatedData);

            // Then
            assertThat(result.getName()).isEqualTo("Обновлённое название");
            assertThat(result.getDescription()).isEqualTo("Новое описание");
            assertThat(result.getDisplayOrder()).isEqualTo(5);
        }

        @Test
        @DisplayName("выбрасывает исключение для несуществующей категории")
        void updateCategory_NotFound_ThrowsException() {
            // Given
            when(categoryRepository.findById(999L)).thenReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> categoryService.updateCategory(999L, new Category()))
                    .isInstanceOf(CategoryNotFoundException.class);
        }
    }

    // ==================== deleteCategory() Tests ====================

    @Nested
    @DisplayName("deleteCategory()")
    class DeleteCategoryTests {

        @Test
        @DisplayName("успешно удаляет пустую категорию без детей")
        void deleteCategory_NoChildrenNoProducts_Success() {
            // Given
            when(categoryRepository.existsByParentId(1L)).thenReturn(false);
            when(productService.countByCategoryId(1L)).thenReturn(0L);
            doNothing().when(categoryRepository).deleteById(1L);
            when(categoryRepository.findAll()).thenReturn(List.of());

            // When
            categoryService.deleteCategory(1L);

            // Then
            verify(categoryRepository).deleteById(1L);
        }

        @Test
        @DisplayName("выбрасывает исключение при наличии подкатегорий")
        void deleteCategory_HasChildren_ThrowsException() {
            // Given
            when(categoryRepository.existsByParentId(1L)).thenReturn(true);

            // When & Then
            assertThatThrownBy(() -> categoryService.deleteCategory(1L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("подкатегории");
        }

        @Test
        @DisplayName("выбрасывает исключение при наличии товаров")
        void deleteCategory_HasProducts_ThrowsException() {
            // Given
            when(categoryRepository.existsByParentId(1L)).thenReturn(false);
            when(productService.countByCategoryId(1L)).thenReturn(5L);

            // When & Then
            assertThatThrownBy(() -> categoryService.deleteCategory(1L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("товар");
        }
    }

    // ==================== getCategoryById() Tests ====================

    @Nested
    @DisplayName("getCategoryById()")
    class GetCategoryByIdTests {

        @Test
        @DisplayName("возвращает категорию по ID")
        void getCategoryById_Exists_ReturnsCategory() {
            // Given
            when(categoryRepository.findById(1L)).thenReturn(Optional.of(rootCategory));

            // When
            Category result = categoryService.getCategoryById(1L);

            // Then
            assertThat(result.getId()).isEqualTo(1L);
            assertThat(result.getName()).isEqualTo("Огнетушители");
        }

        @Test
        @DisplayName("выбрасывает исключение для несуществующего ID")
        void getCategoryById_NotExists_ThrowsException() {
            // Given
            when(categoryRepository.findById(999L)).thenReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> categoryService.getCategoryById(999L))
                    .isInstanceOf(CategoryNotFoundException.class);
        }
    }

    // ==================== getCategoryBySlug() Tests ====================

    @Nested
    @DisplayName("getCategoryBySlug()")
    class GetCategoryBySlugTests {

        @Test
        @DisplayName("возвращает категорию по slug")
        void getCategoryBySlug_Exists_ReturnsCategory() {
            // Given
            when(categoryRepository.findBySlug("ognetushiteli")).thenReturn(Optional.of(rootCategory));

            // When
            Category result = categoryService.getCategoryBySlug("ognetushiteli");

            // Then
            assertThat(result.getSlug()).isEqualTo("ognetushiteli");
        }

        @Test
        @DisplayName("выбрасывает исключение для несуществующего slug")
        void getCategoryBySlug_NotExists_ThrowsException() {
            // Given
            when(categoryRepository.findBySlug("non-existent")).thenReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> categoryService.getCategoryBySlug("non-existent"))
                    .isInstanceOf(CategoryNotFoundException.class);
        }
    }

    // ==================== isLeafCategory() Tests ====================

    @Nested
    @DisplayName("isLeafCategory()")
    class IsLeafCategoryTests {

        @Test
        @DisplayName("возвращает true для категории без детей")
        void isLeafCategory_NoChildren_ReturnsTrue() {
            // Given
            when(categoryRepository.existsByParentId(2L)).thenReturn(false);

            // When
            boolean result = categoryService.isLeafCategory(2L);

            // Then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("возвращает false для категории с детьми")
        void isLeafCategory_HasChildren_ReturnsFalse() {
            // Given
            when(categoryRepository.existsByParentId(1L)).thenReturn(true);

            // When
            boolean result = categoryService.isLeafCategory(1L);

            // Then
            assertThat(result).isFalse();
        }
    }

    // ==================== getCategoryTree() Tests ====================

    @Nested
    @DisplayName("getCategoryTree()")
    class GetCategoryTreeTests {

        @Test
        @DisplayName("возвращает дерево категорий")
        void getCategoryTree_ReturnsTree() {
            // Given - имитируем инициализацию
            when(categoryRepository.findAll()).thenReturn(List.of(rootCategory, childCategory));
            categoryService.refreshCategoryTree();

            // When
            List<CategoryTreeDTO> tree = categoryService.getCategoryTree();

            // Then
            assertThat(tree).hasSize(1); // только root
            assertThat(tree.get(0).getName()).isEqualTo("Огнетушители");
            assertThat(tree.get(0).getChildren()).hasSize(1);
            assertThat(tree.get(0).getChildren().get(0).getName()).isEqualTo("Порошковые");
        }
    }
}
