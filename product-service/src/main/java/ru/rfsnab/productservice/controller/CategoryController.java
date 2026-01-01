package ru.rfsnab.productservice.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.rfsnab.productservice.dto.CategoryRequest;
import ru.rfsnab.productservice.dto.CategoryResponse;
import ru.rfsnab.productservice.dto.CategoryTreeDTO;
import ru.rfsnab.productservice.mapper.CategoryMapper;
import ru.rfsnab.productservice.model.Category;
import ru.rfsnab.productservice.service.CategoryService;

import java.util.List;

@RestController
@RequestMapping("/api/v1/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;

    /**
     * Получить дерево категорий (из кэша)
     */
    @GetMapping("/tree")
    public ResponseEntity<List<CategoryTreeDTO>> getCategoryTree(){
        List<CategoryTreeDTO> tree = categoryService.getCategoryTree();
        return ResponseEntity.ok(tree);
    }

    /**
     * Получить категорию по ID
     */
    @GetMapping("/{id}")
    public ResponseEntity<CategoryResponse> getCategoryById(@PathVariable Long id){
        Category category = categoryService.getCategoryById(id);
        return ResponseEntity.ok(CategoryMapper.toResponse(category));
    }

    /**
     * Получить категорию по slug
     */
    @GetMapping("/slug/{slug}")
    public ResponseEntity<CategoryResponse> getCategoryBySlug(@PathVariable String slug){
        Category category = categoryService.getCategoryBySlug(slug);
        return ResponseEntity.ok(CategoryMapper.toResponse(category));
    }

    /**
     * Создать категорию
     */
    @PostMapping
    public ResponseEntity<CategoryResponse> createCategory(@Valid @RequestBody CategoryRequest request){
        Category category = categoryService.createCategory(CategoryMapper.toEntity(request));
        return ResponseEntity.ok(CategoryMapper.toResponse(category));
    }

    /**
     * Обновить категорию
     */
    @PutMapping("/{id}")
    public ResponseEntity<CategoryResponse> updateCategory(
            @PathVariable Long id,
            @Valid @RequestBody CategoryRequest updatedCategory
    ){
        Category category = categoryService.updateCategory(id, CategoryMapper.toEntity(updatedCategory));
        return ResponseEntity.ok(CategoryMapper.toResponse(category));
    }

    /**
     * Удалить категорию
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCategory(@PathVariable Long id){
        categoryService.deleteCategory(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Установить родительскую категорию
     */
    @PutMapping("/{id}/parent/{parentId}")
    public ResponseEntity<CategoryResponse> setParent(
            @PathVariable Long id,
            @PathVariable Long parentId
    ){
        Category category = categoryService.setParent(id, parentId);
        return ResponseEntity.ok(CategoryMapper.toResponse(category));
    }

    /**
     * Активировать категорию
     */
    @PutMapping("/{id}/activate")
    public ResponseEntity<CategoryResponse> activateCategory(@PathVariable Long id){
        Category category = categoryService.getCategoryById(id);
        category.setIsActive(true);
        Category updated = categoryService.updateCategory(id, category);
        return ResponseEntity.ok(CategoryMapper.toResponse(updated));
    }

    /**
     * Деактивировать категорию
     */
    @PutMapping("/{id}/deactivate")
    public ResponseEntity<CategoryResponse> deactivateCategory(@PathVariable Long id){
        Category category = categoryService.getCategoryById(id);
        category.setIsActive(false);
        Category updated = categoryService.updateCategory(id, category);
        return ResponseEntity.ok(CategoryMapper.toResponse(updated));
    }

}
