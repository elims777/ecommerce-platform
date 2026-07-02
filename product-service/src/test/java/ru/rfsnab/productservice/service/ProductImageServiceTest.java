package ru.rfsnab.productservice.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import ru.rfsnab.productservice.exception.BusinessException;
import ru.rfsnab.productservice.model.Product;
import ru.rfsnab.productservice.model.ProductImage;
import ru.rfsnab.productservice.repository.ProductImageRepository;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProductImageService Unit Tests")
class ProductImageServiceTest {

    @Mock
    private ProductImageRepository imageRepository;

    @Mock
    private ProductService productService;

    @Mock
    private StorageService storageService;

    @InjectMocks
    private ProductImageService imageService;

    private Product testProduct;
    private ProductImage testImage;

    @BeforeEach
    void setUp() {
        testProduct = Product.builder().id(1L).name("Огнетушитель ОП-5").build();

        testImage = ProductImage.builder()
                .id(100L)
                .product(testProduct)
                .fileKey("products/1/image.jpg")
                .fileUrl("https://storage.example.com/products/1/image.jpg")
                .fileSize(102400L)
                .contentType("image/jpeg")
                .width(800)
                .height(600)
                .isPrimary(true)
                .displayOrder(1)
                .build();
    }

    // Передаём пустые байты — ImageIO.read вернёт null, сервис запишет 0,0 (WebP-fallback ветка)
    private final MockMultipartFile emptyFile = new MockMultipartFile(
            "file", "image.webp", "image/webp", new byte[0]);

    @Nested
    @DisplayName("addImage()")
    class AddImageTests {


        @Test
        @DisplayName("загружает первое изображение и устанавливает isPrimary=true")
        void addImage_FirstImage_SetsPrimary() {
            when(productService.getProductById(1L)).thenReturn(testProduct);
            doNothing().when(storageService).validateImage(emptyFile);
            when(storageService.uploadFile(any(), anyString()))
                    .thenReturn("https://storage.example.com/products/1/image.webp");
            when(imageRepository.findAllByProduct(1L)).thenReturn(List.of());
            when(imageRepository.save(any(ProductImage.class))).thenAnswer(inv -> {
                ProductImage img = inv.getArgument(0);
                img.setId(100L);
                return img;
            });

            ProductImage result = imageService.addImage(1L, emptyFile);

            assertThat(result.getId()).isEqualTo(100L);
            assertThat(result.getIsPrimary()).isTrue();
            assertThat(result.getDisplayOrder()).isEqualTo(1);
        }

        @Test
        @DisplayName("загружает второе изображение с isPrimary=false и следующим порядком")
        void addImage_SecondImage_NotPrimary() {
            ProductImage existingImage = ProductImage.builder()
                    .id(99L).product(testProduct).displayOrder(1).isPrimary(true).build();

            when(productService.getProductById(1L)).thenReturn(testProduct);
            doNothing().when(storageService).validateImage(emptyFile);
            when(storageService.uploadFile(any(), anyString()))
                    .thenReturn("https://storage.example.com/products/1/image2.webp");
            when(imageRepository.findAllByProduct(1L)).thenReturn(List.of(existingImage));
            when(imageRepository.save(any(ProductImage.class))).thenAnswer(inv -> inv.getArgument(0));

            ProductImage result = imageService.addImage(1L, emptyFile);

            assertThat(result.getIsPrimary()).isFalse();
            assertThat(result.getDisplayOrder()).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("addImageWithFileKey()")
    class AddImageWithFileKeyTests {

        @Test
        @DisplayName("сохраняет изображение с переданным fileKey вместо сгенерированного из productId")
        void addImageWithFileKey_UsesProvidedFileKey() {
            String explicitFileKey = "products/ftk/FTK-12345/image.webp";

            when(productService.getProductById(1L)).thenReturn(testProduct);
            doNothing().when(storageService).validateImage(emptyFile);
            when(storageService.uploadFile(emptyFile, explicitFileKey))
                    .thenReturn("https://storage.example.com/" + explicitFileKey);
            when(imageRepository.findAllByProduct(1L)).thenReturn(List.of());
            when(imageRepository.save(any(ProductImage.class))).thenAnswer(inv -> {
                ProductImage img = inv.getArgument(0);
                img.setId(200L);
                return img;
            });

            ProductImage result = imageService.addImageWithFileKey(1L, emptyFile, explicitFileKey);

            assertThat(result.getFileKey()).isEqualTo(explicitFileKey);
            assertThat(result.getId()).isEqualTo(200L);
            verify(storageService).uploadFile(emptyFile, explicitFileKey);
        }
    }

    @Nested
    @DisplayName("getImageFileKeys()")
    class GetImageFileKeysTests {

        @Test
        @DisplayName("возвращает список fileKey изображений товара")
        void getImageFileKeys_ReturnsKeys() {
            when(imageRepository.findFileKeysByProduct(1L))
                    .thenReturn(List.of("products/ftk/FTK-1/a.jpg", "products/ftk/FTK-1/b.jpg"));

            List<String> result = imageService.getImageFileKeys(1L);

            assertThat(result).containsExactly("products/ftk/FTK-1/a.jpg", "products/ftk/FTK-1/b.jpg");
        }
    }

    @Nested
    @DisplayName("deleteImage()")
    class DeleteImageTests {

        @Test
        @DisplayName("удаляет изображение из S3 и БД")
        void deleteImage_Exists_DeletesFromStorageAndDb() {
            when(imageRepository.findById(100L)).thenReturn(Optional.of(testImage));

            imageService.deleteImage(100L);

            verify(storageService).deleteFile("products/1/image.jpg");
            verify(imageRepository).deleteById(100L);
        }

        @Test
        @DisplayName("выбрасывает исключение если изображение не найдено")
        void deleteImage_NotFound_ThrowsException() {
            when(imageRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> imageService.deleteImage(999L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("не найдено");

            verify(storageService, never()).deleteFile(any());
        }
    }

    @Nested
    @DisplayName("setPrimaryImage()")
    class SetPrimaryImageTests {

        @Test
        @DisplayName("устанавливает изображение как главное")
        void setPrimaryImage_Exists_SetsPrimary() {
            ProductImage nonPrimary = ProductImage.builder()
                    .id(101L).product(testProduct).isPrimary(false).fileKey("k").fileUrl("u").build();

            when(imageRepository.findById(101L)).thenReturn(Optional.of(nonPrimary));
            doNothing().when(imageRepository).resetPrimaryForProduct(1L);
            when(imageRepository.save(any(ProductImage.class))).thenAnswer(inv -> inv.getArgument(0));

            ProductImage result = imageService.setPrimaryImage(101L);

            assertThat(result.getIsPrimary()).isTrue();
            verify(imageRepository).resetPrimaryForProduct(1L);
        }

        @Test
        @DisplayName("выбрасывает исключение если изображение не найдено")
        void setPrimaryImage_NotFound_ThrowsException() {
            when(imageRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> imageService.setPrimaryImage(999L))
                    .isInstanceOf(BusinessException.class);
        }
    }

    @Nested
    @DisplayName("updateImageOrder()")
    class UpdateImageOrderTests {

        @Test
        @DisplayName("обновляет порядок отображения изображения")
        void updateImageOrder_ValidOrder_UpdatesOrder() {
            when(imageRepository.findById(100L)).thenReturn(Optional.of(testImage));
            when(imageRepository.save(any(ProductImage.class))).thenAnswer(inv -> inv.getArgument(0));

            ProductImage result = imageService.updateImageOrder(100L, 3);

            assertThat(result.getDisplayOrder()).isEqualTo(3);
        }

        @Test
        @DisplayName("выбрасывает исключение при порядке < 1")
        void updateImageOrder_InvalidOrder_ThrowsException() {
            assertThatThrownBy(() -> imageService.updateImageOrder(100L, 0))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining(">= 1");

            verify(imageRepository, never()).findById(any());
        }

        @Test
        @DisplayName("выбрасывает исключение если изображение не найдено")
        void updateImageOrder_NotFound_ThrowsException() {
            when(imageRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> imageService.updateImageOrder(999L, 2))
                    .isInstanceOf(BusinessException.class);
        }
    }

    @Nested
    @DisplayName("getProductImages()")
    class GetProductImagesTests {

        @Test
        @DisplayName("возвращает все изображения товара")
        void getProductImages_ReturnsImages() {
            when(productService.getProductById(1L)).thenReturn(testProduct);
            when(imageRepository.findAllByProduct(1L)).thenReturn(List.of(testImage));

            List<ProductImage> result = imageService.getProductImages(1L);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getFileKey()).isEqualTo("products/1/image.jpg");
        }

        @Test
        @DisplayName("возвращает пустой список если изображений нет")
        void getProductImages_Empty_ReturnsEmptyList() {
            when(productService.getProductById(1L)).thenReturn(testProduct);
            when(imageRepository.findAllByProduct(1L)).thenReturn(List.of());

            List<ProductImage> result = imageService.getProductImages(1L);

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("getImageById()")
    class GetImageByIdTests {

        @Test
        @DisplayName("возвращает изображение по ID")
        void getImageById_Exists_ReturnsImage() {
            when(imageRepository.findById(100L)).thenReturn(Optional.of(testImage));

            ProductImage result = imageService.getImageById(100L);

            assertThat(result.getId()).isEqualTo(100L);
        }

        @Test
        @DisplayName("выбрасывает исключение если изображение не найдено")
        void getImageById_NotFound_ThrowsException() {
            when(imageRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> imageService.getImageById(999L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("не найдено");
        }
    }
}
