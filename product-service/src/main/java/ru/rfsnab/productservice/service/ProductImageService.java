package ru.rfsnab.productservice.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import ru.rfsnab.productservice.exception.BusinessException;
import ru.rfsnab.productservice.exception.InvalidFileException;
import ru.rfsnab.productservice.model.Product;
import ru.rfsnab.productservice.model.ProductImage;
import ru.rfsnab.productservice.repository.ProductImageRepository;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ProductImageService {
    private final ProductImageRepository imageRepository;
    private final ProductService productService;
    private final StorageService storageService;
    private record ImageDimensions(int width, int height) {}

    /**
     * Добавить изображение к товару
     * Загружает файл в YOS и сохраняет метаданные в БД
     * @param productId товара
     * @param file изображение
     * @return ProductImage
     */
    @Transactional
    public ProductImage addImage(Long productId, MultipartFile file) {
        Product product = productService.getProductById(productId);

        // Валидация файла
        storageService.validateImage(file);

        // Получаем размеры изображения
        ImageDimensions dimensions = getImageDimensions(file);

        // Загрузка в Yandex Object Storage
        String fileKey = "products/" + productId + "/" + file.getOriginalFilename();
        String fileUrl = storageService.uploadFile(file, fileKey);

        // Определяем displayOrder (последний + 1)
        List<ProductImage> existingImages = imageRepository.findAllByProduct(productId);
        int nextOrder = existingImages.isEmpty() ? 1 : existingImages.get(existingImages.size() - 1).getDisplayOrder() + 1;

        // Создаем запись в БД
        ProductImage image = ProductImage.builder()
                .product(product)
                .fileKey(fileKey)
                .fileUrl(fileUrl)
                .fileSize(file.getSize())
                .contentType(file.getContentType())
                .width(dimensions.width())
                .height(dimensions.height())
                .isPrimary(existingImages.isEmpty())
                .displayOrder(nextOrder)
                .build();

        return imageRepository.save(image);
    }

    /**
     * Получить размеры изображения из файла
     * @param file изображение
     * @return ImageDimensions (width, height)
     */
    private ImageDimensions getImageDimensions(MultipartFile file) {
        try {
            BufferedImage image = ImageIO.read(file.getInputStream());

            if (image == null) {
                throw new InvalidFileException("Не удалось прочитать изображение");
            }

            return new ImageDimensions(image.getWidth(), image.getHeight());

        } catch (IOException e) {
            throw new InvalidFileException("Ошибка при чтении размеров изображения: " + e.getMessage());
        }
    }

    /**
     * Удалить изображение (из БД и из YOS)
     * @param imageId изображения
     */
    @Transactional
    public void deleteImage(Long imageId) {
        ProductImage image = imageRepository.findById(imageId)
                .orElseThrow(() -> new BusinessException("Изображение не найдено"));

        // Удаляем из Yandex Object Storage
        storageService.deleteFile(image.getFileKey());

        // Удаляем из БД
        imageRepository.deleteById(imageId);
    }

    /**
     * Установить главное изображение
     * Сбрасывает isPrimary у остальных изображений товара
     * @param imageId изображения
     * @return ProductImage
     */
    @Transactional
    public ProductImage setPrimaryImage(Long imageId) {
        ProductImage image = imageRepository.findById(imageId)
                .orElseThrow(() -> new BusinessException("Изображение не найдено"));

        Long productId = image.getProduct().getId();
        imageRepository.resetPrimaryForProduct(productId);

        image.setIsPrimary(true);
        return imageRepository.save(image);
    }

    /**
     * Изменить порядок отображения изображения
     * @param imageId изображения
     * @param newOrder новый порядок
     * @return ProductImage
     */
    @Transactional
    public ProductImage updateImageOrder(Long imageId, Integer newOrder) {
        if (newOrder < 1) {
            throw new BusinessException("Порядок должен быть >= 1");
        }

        ProductImage image = imageRepository.findById(imageId)
                .orElseThrow(() -> new BusinessException("Изображение не найдено"));

        image.setDisplayOrder(newOrder);
        return imageRepository.save(image);
    }

    /**
     * Обновить alt текст для SEO
     * @param imageId изображения
     * @param altText текст
     * @return ProductImage
     */
    @Transactional
    public ProductImage updateAltText(Long imageId, String altText) {
        ProductImage image = imageRepository.findById(imageId)
                .orElseThrow(() -> new BusinessException("Изображение не найдено"));

        image.setAltText(altText);
        return imageRepository.save(image);
    }

    /**
     * Получить все изображения товара
     * @param productId товара
     * @return List<ProductImage>
     */
    public List<ProductImage> getProductImages(Long productId) {
        productService.getProductById(productId);
        return imageRepository.findAllByProduct(productId);
    }

    /**
     * Получить главное изображение товара
     * @param productId товара
     * @return ProductImage
     */
    public ProductImage getPrimaryImage(Long productId) {
        productService.getProductById(productId); 

        return imageRepository.findPrimaryByProduct(productId)
                .orElseThrow(() -> new BusinessException("Главное изображение не найдено"));
    }

    /**
     * Получить изображение по ID
     * @param imageId изображения
     * @return ProductImage
     */
    public ProductImage getImageById(Long imageId) {
        return imageRepository.findById(imageId)
                .orElseThrow(() -> new BusinessException("Изображение не найдено"));
    }
}
