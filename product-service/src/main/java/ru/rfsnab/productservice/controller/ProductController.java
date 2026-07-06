package ru.rfsnab.productservice.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.rfsnab.productservice.dto.AvailableCountResponse;
import ru.rfsnab.productservice.dto.ProductRequest;
import ru.rfsnab.productservice.dto.ProductResponse;
import ru.rfsnab.productservice.mapper.ProductMapper;
import ru.rfsnab.productservice.model.Product;
import ru.rfsnab.productservice.service.ProductService;

import java.util.List;
import java.util.Map;


@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
public class ProductController {
    private final ProductService productService;

    /**
     * Получить все активные товары с пагинацией
     */
    @GetMapping
    public ResponseEntity<Page<ProductResponse>> getAllProducts(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)Pageable pageable
            ){
        Page<Product> productsPage = productService.getProductsPage(pageable);
        var parentIds = productService.findParentIdsWithActiveChildren(
                productsPage.getContent().stream().map(Product::getId).toList());
        return ResponseEntity.ok(ProductMapper.mapPageWithHasVariants(productsPage, parentIds));
    }

    /**
     * Получить все товары с пагинацией для админки
     */
    @GetMapping("/admin")
    public ResponseEntity<Page<ProductResponse>> getAllProductsAdmin(
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Boolean isActive,
            @PageableDefault(size = 20, sort = "name", direction = Sort.Direction.ASC) Pageable pageable) {
        Page<Product> productsPage = productService.getAllProductsAdminPage(categoryId, isActive, pageable);
        return ResponseEntity.ok(productsPage.map(ProductMapper::mapToResponse));
    }

    /**
     * Получить товар по ID (с дочерними вариантами если есть)
     */
    @GetMapping("/{id}")
    public ResponseEntity<ProductResponse> getProductById(@PathVariable Long id){
        Product product = productService.getProductById(id);
        List<Product> children = productService.getChildren(id);
        return ResponseEntity.ok(ProductMapper.mapToResponse(product, children));
    }

    /**
     * Получить товар по slug (с дочерними вариантами если есть)
     */
    @GetMapping("/slug/{slug}")
    public ResponseEntity<ProductResponse> getProductBySlug(@PathVariable String slug){
        Product product = productService.getProductBySlug(slug);
        List<Product> children = productService.getChildren(product.getId());
        return ResponseEntity.ok(ProductMapper.mapToResponse(product, children));
    }

    /**
     * Обновить порядок отображения товара в категории
     */
    @PatchMapping("/reorder")
    public ResponseEntity<Void> reorderProducts(@RequestBody Map<Long, Integer> orders) {
        productService.reorderProducts(orders);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/display-order")
    public ResponseEntity<ProductResponse> updateDisplayOrder(
            @PathVariable Long id,
            @RequestBody Map<String, Integer> body
    ) {
        Product product = productService.updateDisplayOrder(id, body.get("displayOrder"));
        return ResponseEntity.ok(ProductMapper.mapToResponse(product));
    }

    /**
     * Назначить/снять родительский товар (объединение в варианты)
     */
    @PatchMapping("/{id}/set-parent")
    public ResponseEntity<ProductResponse> setParent(
            @PathVariable Long id,
            @RequestParam(required = false) Long parentProductId
    ) {
        Product product = productService.setParent(id, parentProductId);
        return ResponseEntity.ok(ProductMapper.mapToResponse(product));
    }

    /**
     * Получить товары по категории
     */
    @GetMapping("/category/{categoryId}")
    public ResponseEntity<Page<ProductResponse>> getProductsByCategory(
            @PathVariable Long categoryId,
            @PageableDefault(size = 20, sort = "displayOrder", direction = Sort.Direction.ASC) Pageable pageable
            ){
        Page<Product> products = productService.getProductsByCategoryPage(categoryId, pageable);
        var parentIds = productService.findParentIdsWithActiveChildren(
                products.getContent().stream().map(Product::getId).toList());
        return ResponseEntity.ok(ProductMapper.mapPageWithHasVariants(products, parentIds));
    }

    /**
     * Количество активных товаров в наличии (для счётчика на главной странице)
     */
    @GetMapping("/count-available")
    public ResponseEntity<AvailableCountResponse> countAvailableProducts() {
        return ResponseEntity.ok(new AvailableCountResponse(productService.countAvailableProducts()));
    }

    /**
     * Поиск товаров по названию
     */
    @GetMapping("/search")
    public ResponseEntity<Page<ProductResponse>> searchProducts(
            @RequestParam String query,
            @PageableDefault(size = 20) Pageable pageable){
        Page<Product> products = productService.searchProducts(query, pageable);
        return ResponseEntity.ok(products.map(ProductMapper::mapToResponse));
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

    /**
     * Массовое перемещение товаров в категорию
     */
    @PutMapping("/batch/category")
    public ResponseEntity<Void> batchUpdateCategory(
            @RequestParam Long categoryId,
            @RequestBody List<Long> productIds
    ) {
        productService.batchUpdateCategory(productIds, categoryId);
        return ResponseEntity.ok().build();
    }

    /**
     * Массовая активация/деактивация товаров
     */
    @PutMapping("/batch/active")
    public ResponseEntity<Void> batchUpdateActive(
            @RequestParam Boolean isActive,
            @RequestBody List<Long> productIds
    ) {
        productService.batchUpdateActive(productIds, isActive);
        return ResponseEntity.ok().build();
    }

    /**
     * Массовое удаление товаров
     */
    @DeleteMapping("/batch")
    public ResponseEntity<Void> batchDelete(@RequestBody List<Long> productIds) {
        productService.batchDelete(productIds);
        return ResponseEntity.noContent().build();
    }
}
