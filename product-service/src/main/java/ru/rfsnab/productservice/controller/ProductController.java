package ru.rfsnab.productservice.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.rfsnab.productservice.dto.ProductRequest;
import ru.rfsnab.productservice.dto.ProductResponse;
import ru.rfsnab.productservice.mapper.ProductMapper;
import ru.rfsnab.productservice.model.Product;
import ru.rfsnab.productservice.service.ProductService;

import java.util.List;

@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
public class ProductController {
    private final ProductService productService;

    /**
     * Получить все товары с пагинацией
     */
    @GetMapping
    public ResponseEntity<Page<ProductResponse>> getAllProducts(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)Pageable pageable
            ){
        Page<Product> productsPage = productService.getProductsPage(pageable);
        return ResponseEntity.ok(productsPage.map(ProductMapper::mapToResponse));
    }

    /**
     * Получить товар по ID
     */
    @GetMapping("/{id}")
    public ResponseEntity<ProductResponse> getProductById(@PathVariable Long id){
        Product product = productService.getProductById(id);
        return ResponseEntity.ok(ProductMapper.mapToResponse(product));
    }

    /**
     * Получить товар по slug
     */
    @GetMapping("/slug/{slug}")
    public ResponseEntity<ProductResponse> getProductBySlug(@PathVariable String slug){
        Product product = productService.getProductBySlug(slug);
        return ResponseEntity.ok(ProductMapper.mapToResponse(product));
    }

    /**
     * Получить товары по категории
     */
    @GetMapping("/category/{categoryId}")
    public ResponseEntity<Page<ProductResponse>> getProductsByCategory(
            @PathVariable Long categoryId,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
            ){
        Page<Product> products = productService.getProductsByCategory(categoryId, pageable);
        return ResponseEntity.ok(products.map(ProductMapper::mapToResponse));
    }

    /**
     * Поиск товаров по названию
     */
    @GetMapping("/search")
    public ResponseEntity<List<ProductResponse>> searchProducts(@RequestParam String query){
        List<Product> products = productService.searchProducts(query);
        return ResponseEntity.ok(products.stream().map(ProductMapper::mapToResponse).toList());
    }

    /**
     * Создать товар
     * categoryId может быть null - товар без категории (для импорта)
     */
    @PostMapping
    public ResponseEntity<ProductResponse> createProduct(@RequestBody ProductRequest request){
        Product product = productService.createProduct(ProductMapper.mapToEntity(request));
        return ResponseEntity.ok(ProductMapper.mapToResponse(product));
    }

    /**
     * Обновить товар
     */
    @PutMapping("/{id}")
    public ResponseEntity<ProductResponse> updateProduct(
            @PathVariable Long id,
            @Valid @RequestBody ProductRequest request
    ){
        Product product = productService.updateProduct(id, ProductMapper.mapToEntity(request));
        return ResponseEntity.ok(ProductMapper.mapToResponse(product));
    }

    /**
     * Удалить товар
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteProduct(@PathVariable Long id){
        productService.deleteProduct(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Изменить количество на складе
     */
    @PutMapping("/{id}/stock")
    public ResponseEntity<ProductResponse> changeStock(
            @PathVariable Long id,
            @RequestParam Integer quantity
    ){
        Product product = productService.getProductById(id);
        product.setStockQuantity(quantity);
        Product updated = productService.updateProduct(id, product);
        return ResponseEntity.ok(ProductMapper.mapToResponse(updated));
    }

    /**
     * Изменить категорию товара (с валидацией leaf)
     */
    @PutMapping("/{id}/category")
    public ResponseEntity<ProductResponse> updateProductCategory(
            @PathVariable Long id,
            @RequestParam(required = false) Long categoryId
    ) {
        Product product = productService.updateProductCategory(id, categoryId);
        return ResponseEntity.ok(ProductMapper.mapToResponse(product));
    }

    /**
     * Активировать товар
     */
    @PutMapping("/{id}/activate")
    public ResponseEntity<ProductResponse> activateProduct(@PathVariable Long id) {
        Product updated = productService.activateProduct(id);
        return ResponseEntity.ok(ProductMapper.mapToResponse(updated));
    }

    /**
     * Деактивировать товар
     */
    @PutMapping("/{id}/deactivate")
    public ResponseEntity<ProductResponse> deactivateProduct(@PathVariable Long id) {
        Product updated = productService.deactivateProduct(id);
        return ResponseEntity.ok(ProductMapper.mapToResponse(updated));
    }

    /**
     * Сохранить или обновить товар (для импорта из 1С)
     */
    @PutMapping("/import/{id}")
    public ResponseEntity<ProductResponse> saveOrUpdateProduct(
            @PathVariable Long id,
            @Valid @RequestBody ProductRequest request
    ) {
        Product product = ProductMapper.mapToEntity(request);
        Product saved = productService.saveOrUpdate(id, product);
        return ResponseEntity.ok(ProductMapper.mapToResponse(saved));
    }

}
