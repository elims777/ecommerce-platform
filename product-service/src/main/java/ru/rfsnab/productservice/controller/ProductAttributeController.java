package ru.rfsnab.productservice.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.rfsnab.productservice.dto.ProductAttributeRequest;
import ru.rfsnab.productservice.dto.ProductAttributeResponse;
import ru.rfsnab.productservice.mapper.AttributeMapper;
import ru.rfsnab.productservice.model.ProductAttribute;
import ru.rfsnab.productservice.service.ProductAttributeService;

import java.util.List;

@RestController
@RequestMapping("/api/v1/products/{productId}/attributes")
@RequiredArgsConstructor
public class ProductAttributeController {
    private final ProductAttributeService attributeService;

    /**
     * Получить все атрибуты товара
     */
    @GetMapping
    public ResponseEntity<List<ProductAttributeResponse>> getAllAttributes(@PathVariable Long productId){
        List<ProductAttribute> productAttributes = attributeService.getProductAttributes(productId);
        return ResponseEntity.ok(productAttributes.stream().map(AttributeMapper::mapToResponse).toList());
    }

    /**
     * Добавить атрибут к товару
     */
    @PostMapping
    public ResponseEntity<ProductAttributeResponse> addAttribute(
            @PathVariable Long productId,
            @Valid @RequestBody ProductAttributeRequest attribute
    ){
        ProductAttribute productAttribute =
                attributeService.addAttribute(productId, AttributeMapper.toEntity(attribute));
        return ResponseEntity.status(HttpStatus.CREATED).body(AttributeMapper.mapToResponse(productAttribute));
    }

    /**
     * Обновить атрибут
     */
    @PutMapping("/{attributeId}")
    public ResponseEntity<ProductAttributeResponse> updateAttribute(
            @PathVariable Long productId,
            @PathVariable Long attributeId,
            @Valid @RequestBody ProductAttributeRequest attributeRequest
    ){
        ProductAttribute productAttribute =
                attributeService.updateAttribute(attributeId, AttributeMapper.toEntity(attributeRequest));
        return ResponseEntity.ok(AttributeMapper.mapToResponse(productAttribute));
    }

    /**
     * Удалить атрибут
     */
    @DeleteMapping("/{attributeId}")
    public ResponseEntity<Void> deleteAttribute(@PathVariable Long productId, @PathVariable Long attributeId){
        attributeService.deleteAttribute(attributeId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Удалить все атрибуты товара
     */
    @DeleteMapping
    public ResponseEntity<Void> deleteAllAttributes(@PathVariable Long productId){
        attributeService.deleteAllAttributes(productId);
        return ResponseEntity.noContent().build();
    }
}
