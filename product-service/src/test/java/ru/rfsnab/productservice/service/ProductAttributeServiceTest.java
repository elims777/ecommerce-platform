package ru.rfsnab.productservice.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.rfsnab.productservice.exception.BusinessException;
import ru.rfsnab.productservice.model.Product;
import ru.rfsnab.productservice.model.ProductAttribute;
import ru.rfsnab.productservice.repository.ProductAttributeRepository;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProductAttributeService Unit Tests")
class ProductAttributeServiceTest {

    @Mock
    private ProductAttributeRepository attributeRepository;

    @Mock
    private ProductService productService;

    @InjectMocks
    private ProductAttributeService attributeService;

    private Product testProduct;
    private ProductAttribute testAttribute;

    @BeforeEach
    void setUp() {
        testProduct = Product.builder().id(1L).name("Огнетушитель ОП-5").build();

        testAttribute = ProductAttribute.builder()
                .id(10L)
                .product(testProduct)
                .attributeName("Масса заряда")
                .attributeValue("5 кг")
                .build();
    }

    @Nested
    @DisplayName("addAttribute()")
    class AddAttributeTests {

        @Test
        @DisplayName("добавляет атрибут к существующему товару")
        void addAttribute_ValidProduct_SavesAttribute() {
            ProductAttribute newAttr = ProductAttribute.builder()
                    .attributeName("Масса заряда")
                    .attributeValue("5 кг")
                    .build();

            when(productService.getProductById(1L)).thenReturn(testProduct);
            when(attributeRepository.save(any(ProductAttribute.class))).thenAnswer(inv -> {
                ProductAttribute a = inv.getArgument(0);
                a.setId(10L);
                return a;
            });

            ProductAttribute result = attributeService.addAttribute(1L, newAttr);

            assertThat(result.getId()).isEqualTo(10L);
            assertThat(result.getProduct()).isEqualTo(testProduct);
            verify(attributeRepository).save(newAttr);
        }
    }

    @Nested
    @DisplayName("updateAttribute()")
    class UpdateAttributeTests {

        @Test
        @DisplayName("обновляет атрибут при корректных данных")
        void updateAttribute_ValidData_UpdatesAttribute() {
            ProductAttribute update = ProductAttribute.builder()
                    .attributeName("Объём")
                    .attributeValue("5 л")
                    .build();

            when(attributeRepository.findById(10L)).thenReturn(Optional.of(testAttribute));
            when(attributeRepository.save(any(ProductAttribute.class))).thenAnswer(inv -> inv.getArgument(0));

            ProductAttribute result = attributeService.updateAttribute(10L, update);

            assertThat(result.getAttributeName()).isEqualTo("Объём");
            assertThat(result.getAttributeValue()).isEqualTo("5 л");
        }

        @Test
        @DisplayName("выбрасывает исключение если атрибут не найден")
        void updateAttribute_NotFound_ThrowsException() {
            when(attributeRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> attributeService.updateAttribute(99L, new ProductAttribute()))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("не найден");
        }

        @Test
        @DisplayName("выбрасывает исключение если данные для обновления null")
        void updateAttribute_NullData_ThrowsException() {
            when(attributeRepository.findById(10L)).thenReturn(Optional.of(testAttribute));

            assertThatThrownBy(() -> attributeService.updateAttribute(10L, null))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("не должны быть пустыми");
        }

        @Test
        @DisplayName("выбрасывает исключение если attributeName null")
        void updateAttribute_NullName_ThrowsException() {
            when(attributeRepository.findById(10L)).thenReturn(Optional.of(testAttribute));
            ProductAttribute update = ProductAttribute.builder().attributeValue("5 кг").build();

            assertThatThrownBy(() -> attributeService.updateAttribute(10L, update))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("не должны быть пустыми");
        }
    }

    @Nested
    @DisplayName("deleteAttribute()")
    class DeleteAttributeTests {

        @Test
        @DisplayName("удаляет существующий атрибут")
        void deleteAttribute_Exists_Deletes() {
            when(attributeRepository.existsById(10L)).thenReturn(true);

            attributeService.deleteAttribute(10L);

            verify(attributeRepository).deleteById(10L);
        }

        @Test
        @DisplayName("выбрасывает исключение если атрибут не найден")
        void deleteAttribute_NotFound_ThrowsException() {
            when(attributeRepository.existsById(99L)).thenReturn(false);

            assertThatThrownBy(() -> attributeService.deleteAttribute(99L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("не найден");

            verify(attributeRepository, never()).deleteById(any());
        }
    }

    @Nested
    @DisplayName("getProductAttributes()")
    class GetProductAttributesTests {

        @Test
        @DisplayName("возвращает все атрибуты товара")
        void getProductAttributes_ReturnsAttributes() {
            when(productService.getProductById(1L)).thenReturn(testProduct);
            when(attributeRepository.findAllByProduct(1L)).thenReturn(List.of(testAttribute));

            List<ProductAttribute> result = attributeService.getProductAttributes(1L);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getAttributeName()).isEqualTo("Масса заряда");
        }

        @Test
        @DisplayName("возвращает пустой список если атрибутов нет")
        void getProductAttributes_Empty_ReturnsEmptyList() {
            when(productService.getProductById(1L)).thenReturn(testProduct);
            when(attributeRepository.findAllByProduct(1L)).thenReturn(List.of());

            List<ProductAttribute> result = attributeService.getProductAttributes(1L);

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("getAttributeById()")
    class GetAttributeByIdTests {

        @Test
        @DisplayName("возвращает атрибут по ID")
        void getAttributeById_Exists_ReturnsAttribute() {
            when(attributeRepository.findById(10L)).thenReturn(Optional.of(testAttribute));

            ProductAttribute result = attributeService.getAttributeById(10L);

            assertThat(result.getId()).isEqualTo(10L);
            assertThat(result.getAttributeName()).isEqualTo("Масса заряда");
        }

        @Test
        @DisplayName("выбрасывает исключение если атрибут не найден")
        void getAttributeById_NotFound_ThrowsException() {
            when(attributeRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> attributeService.getAttributeById(99L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("не найден");
        }
    }
}
