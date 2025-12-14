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
                .build();
    }

    public static ProductResponse mapToResponse(Product product){
        return ProductResponse.builder()
                .id(product.getId())
                .name(product.getName())
                .slug(product.getSlug())
                .description(product.getDescription())
                .shortDescription(product.getShortDescription())
                .price(product.getPrice())
                .stockQuantity(product.getStockQuantity())
                .categoryId(product.getCategory().getId())
                .categoryName(product.getCategory().getName())
                .isActive(product.getIsActive())
                .isFeatured(product.getIsFeatured())
                .createdAt(product.getCreatedAt())
                .updatedAt(product.getUpdatedAt())
                .images(product.getImages().stream().map(ImageMapper::mapToResponse).toList())
                .videos(product.getVideos().stream().map(VideoMapper::mapToResponse).toList())
                .attributes(product.getAttributes().stream().map(AttributeMapper::mapToResponse).toList())
                .build();
    }

}
