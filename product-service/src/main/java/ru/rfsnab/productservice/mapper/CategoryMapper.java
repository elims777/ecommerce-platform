package ru.rfsnab.productservice.mapper;

import ru.rfsnab.productservice.dto.CategoryRequest;
import ru.rfsnab.productservice.dto.CategoryResponse;
import ru.rfsnab.productservice.model.Category;

public class CategoryMapper {

    public static Category toEntity(CategoryRequest request) {
        Category category = Category.builder()
                .name(request.getName())
                .description(request.getDescription())
                .build();

        // Устанавливаем parent если указан
        if (request.getParentId() != null) {
            Category parent = new Category();
            parent.setId(request.getParentId());
            category.setParent(parent);
        }

        return category;
    }

    public static CategoryResponse toResponse(Category category) {
        CategoryResponse.CategoryResponseBuilder builder = CategoryResponse.builder()
                .id(category.getId())
                .name(category.getName())
                .slug(category.getSlug())
                .description(category.getDescription())
                .isActive(category.getIsActive())
                .displayOrder(category.getDisplayOrder())
                .createdAt(category.getCreatedAt())
                .updatedAt(category.getUpdatedAt());

        // Добавляем parent если есть
        if (category.getParent() != null) {
            builder.parentId(category.getParent().getId());
            builder.parentName(category.getParent().getName());
        }

        return builder.build();
    }
}
