package ru.rfsnab.productservice.mapper;

import ru.rfsnab.productservice.dto.ProductRequest;
import ru.rfsnab.productservice.dto.ProductResponse;
import ru.rfsnab.productservice.model.Product;

public class ProductMapper {

    public static Product mapToEntity(ProductRequest productRequest){
        return Product.builder()
                .name(productRequest.getName())
                .description(productRequest.getDescription())
                .shortDescription(productRequest.getShortDescription())
                .price(productRequest.getPrice())
                .stockQuantity(productRequest.getStockQuantity())
                .isActive(productRequest.getIsActive())
                .isFeatured(productRequest.getIsFeatured())
                .externalId(productRequest.getExternalId())
                .sku(productRequest.getSku())
                .externalCode(productRequest.getExternalCode())
                .unitOfMeasure(productRequest.getUnitOfMeasure())
                .vatRate(productRequest.getVatRate())
                .build();
    }

    public static ProductResponse mapToResponse(Product product) {
        ProductResponse.ProductResponseBuilder builder = ProductResponse.builder()
                .id(product.getId())
                .name(product.getName())
                .slug(product.getSlug())
                .description(product.getDescription())
                .shortDescription(product.getShortDescription())
                .price(product.getPrice())
                .stockQuantity(product.getStockQuantity())
                .isActive(product.getIsActive())
                .isFeatured(product.getIsFeatured())
                .externalId(product.getExternalId())
                .sku(product.getSku())
                .externalCode(product.getExternalCode())
                .unitOfMeasure(product.getUnitOfMeasure())
                .vatRate(product.getVatRate())
                .createdAt(product.getCreatedAt())
                .updatedAt(product.getUpdatedAt())
                .images(product.getImages().stream().map(ImageMapper::mapToResponse).toList())
                .videos(product.getVideos().stream().map(VideoMapper::mapToResponse).toList())
                .attributes(product.getAttributes().stream().map(AttributeMapper::mapToResponse).toList());

        // Добавляем категорию если есть
        if (product.getCategory() != null) {
            builder.categoryId(product.getCategory().getId());
            builder.categoryName(product.getCategory().getName());
        }

        return builder.build();
    }
}
