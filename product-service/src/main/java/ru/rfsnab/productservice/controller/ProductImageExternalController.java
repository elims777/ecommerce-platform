package ru.rfsnab.productservice.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import ru.rfsnab.productservice.dto.ProductImageResponse;
import ru.rfsnab.productservice.exception.ProductNotFoundException;
import ru.rfsnab.productservice.mapper.ImageMapper;
import ru.rfsnab.productservice.model.Product;
import ru.rfsnab.productservice.model.ProductImage;
import ru.rfsnab.productservice.service.ProductImageService;
import ru.rfsnab.productservice.service.ProductService;

import java.util.List;
import java.util.Map;

/**
 * Контроллер для загрузки изображений по externalId.
 * Используется integration-service при импорте картинок из 1С.
 * Endpoint защищён через gateway (JWT), доступ только service-to-service.
 */
@RestController
@RequestMapping("/api/v1/products/external")
@RequiredArgsConstructor
public class ProductImageExternalController {

    private final ProductService productService;
    private final ProductImageService imageService;

    /**
     * Загрузить изображение к товару по externalId (1С UUID).
     * ProductService проверяет существование товара,
     * ProductImageService выполняет загрузку.
     */
    @PostMapping(
            value = "/{externalId}/images",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ResponseEntity<ProductImageResponse> uploadImageByExternalId(
            @PathVariable String externalId,
            @RequestParam("file") MultipartFile file
    ) {
        Product product = productService.findByExternalId(externalId);
        String fileKey = "products/ftk/" + externalId + "/" + file.getOriginalFilename();
        ProductImage productImage = imageService.addImageWithFileKey(product.getId(), file, fileKey);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ImageMapper.mapToResponse(productImage));
    }

    /**
     * Получить fileKey уже загруженных изображений товара по externalId.
     * Используется integration-service для сверки перед повторной загрузкой (ФТК-импорт),
     * чтобы не перекачивать и не заливать в S3 уже существующие картинки.
     * Если товар не найден — возвращается пустой список (не 404).
     */
    @GetMapping("/{externalId}/images/keys")
    public ResponseEntity<List<String>> getImageKeysByExternalId(@PathVariable String externalId) {
        try {
            Product product = productService.findByExternalId(externalId);
            return ResponseEntity.ok(imageService.getImageFileKeys(product.getId()));
        } catch (ProductNotFoundException e) {
            return ResponseEntity.ok(List.of());
        }
    }

    /**
     * Получить fileKey уже загруженных изображений сразу для нескольких товаров по externalId.
     * Используется integration-service для batch-сверки перед ФТК-импортом —
     * один запрос вместо отдельного GET на каждый товар.
     * Товары без картинок или не найденные в ответе отсутствуют (пустой список для них).
     */
    @PostMapping("/images/keys/batch")
    public ResponseEntity<Map<String, List<String>>> getImageKeysByExternalIds(@RequestBody List<String> externalIds) {
        return ResponseEntity.ok(imageService.getImageFileKeysByExternalIds(externalIds));
    }
}