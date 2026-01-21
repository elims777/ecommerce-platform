package ru.rfsnab.productservice.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import ru.rfsnab.productservice.dto.ProductImageResponse;
import ru.rfsnab.productservice.mapper.ImageMapper;
import ru.rfsnab.productservice.model.ProductImage;
import ru.rfsnab.productservice.service.ProductImageService;

import java.util.List;

@RestController
@RequestMapping("/api/v1/products/{productId}/images")
@RequiredArgsConstructor
public class ProductImageController {

    private final ProductImageService imageService;

    /**
     * Получить все изображения товара
     */
    @GetMapping
    public ResponseEntity<List<ProductImageResponse>> getProductImages(@PathVariable Long productId){
        List<ProductImage> productImages = imageService.getProductImages(productId);
        return ResponseEntity.ok(productImages.stream().map(ImageMapper::mapToResponse).toList());
    }

    /**
     * Получить главное изображение товара
     */
    @GetMapping("/primary")
    public ResponseEntity<ProductImageResponse> getPrimaryImage(@PathVariable Long productId){
        ProductImage primaryImage = imageService.getPrimaryImage(productId);
        return ResponseEntity.ok(ImageMapper.mapToResponse(primaryImage));
    }

    /**
     * Загрузить изображение к товару
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ProductImageResponse> uploadImage(
            @PathVariable Long productId,
            @RequestParam("file") MultipartFile file
            ){
        ProductImage productImage = imageService.addImage(productId, file);
        return ResponseEntity.status(HttpStatus.CREATED).body(ImageMapper.mapToResponse(productImage));
    }

    /**
     * Удалить изображение
     */
    @DeleteMapping("/{imageId}")
    public ResponseEntity<Void> deleteImage(
            @PathVariable Long productId,
            @PathVariable Long imageId
            ){
        imageService.deleteImage(imageId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Установить главное изображение
     */
    @PutMapping("/{imageId}/primary")
    public ResponseEntity<ProductImageResponse> setPrimaryImage(
            @PathVariable Long productId,
            @PathVariable Long imageId
            ){
        ProductImage productImage = imageService.setPrimaryImage(imageId);
        return ResponseEntity.ok(ImageMapper.mapToResponse(productImage));
    }

    /**
     * Изменить порядок отображения
     */
    @PutMapping("/{imageId}/order")
    public ResponseEntity<ProductImageResponse> updateImageOrder(
            @PathVariable Long productId,
            @PathVariable Long imageId,
            @RequestParam Integer order
            ){
        ProductImage productImage = imageService.updateImageOrder(imageId, order);
        return ResponseEntity.ok(ImageMapper.mapToResponse(productImage));
    }

    /**
     * Обновить alt текст
     */
    @PutMapping("/{imageId}/alt")
    public ResponseEntity<ProductImageResponse> updateImageAlt(
            @PathVariable Long productId,
            @PathVariable Long imageId,
            @RequestParam String altText
    ){
        ProductImage productImage = imageService.updateAltText(imageId, altText);
        return ResponseEntity.ok(ImageMapper.mapToResponse(productImage));
    }
}
