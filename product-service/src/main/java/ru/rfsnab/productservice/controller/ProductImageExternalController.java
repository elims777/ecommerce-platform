package ru.rfsnab.productservice.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import ru.rfsnab.productservice.dto.ProductImageResponse;
import ru.rfsnab.productservice.mapper.ImageMapper;
import ru.rfsnab.productservice.model.Product;
import ru.rfsnab.productservice.model.ProductImage;
import ru.rfsnab.productservice.service.ProductImageService;
import ru.rfsnab.productservice.service.ProductService;

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
        ProductImage productImage = imageService.addImage(product.getId(), file);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ImageMapper.mapToResponse(productImage));
    }
}