package ru.rfsnab.productservice.mapper;

import ru.rfsnab.productservice.dto.ProductRequest;
import ru.rfsnab.productservice.dto.ProductResponse;
import ru.rfsnab.productservice.dto.ProductVariantDto;
import ru.rfsnab.productservice.model.Category;
import ru.rfsnab.productservice.model.Product;
import ru.rfsnab.productservice.model.ProductAttribute;
import ru.rfsnab.productservice.model.ProductVariant;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ProductMapper {

    public static Product mapToEntity(ProductRequest productRequest){
        return Product.builder()
                .name(productRequest.getName())
                .description(productRequest.getDescription())
                .shortDescription(productRequest.getShortDescription())
                .material(productRequest.getMaterial())
                .price(productRequest.getPrice())
                .wholesalePrice(productRequest.getWholesalePrice())
                .stockQuantity(productRequest.getStockQuantity())
                .isActive(productRequest.getIsActive())
                .isFeatured(productRequest.getIsFeatured())
                .externalId(productRequest.getExternalId())
                .sku(productRequest.getSku())
                .externalCode(productRequest.getExternalCode())
                .unitOfMeasure(productRequest.getUnitOfMeasure())
                .vatRate(productRequest.getVatRate())
                .category(productRequest.getCategoryId() != null
                        ? Category.builder().id(productRequest.getCategoryId()).build()
                        : null)
                .build();
    }

    public static ProductResponse mapToResponse(Product product) {
        return mapToResponse(product, List.of());
    }

    public static ProductResponse mapToResponse(Product product, List<Product> children) {
        // Варианты из таблицы product_variants
        List<ProductVariantDto> variants = product.getVariants().stream()
                .map(ProductMapper::mapVariantToDto)
                .collect(Collectors.toList());

        // Дочерние товары-варианты: маппим default-вариант каждого дочернего в ProductVariantDto
        for (Product child : children) {
            child.getVariants().stream()
                    .filter(v -> v.getExternalId() != null && v.getExternalId().endsWith("#default"))
                    .findFirst()
                    .ifPresent(defaultVariant -> {
                        Map<String, String> attrs = child.getAttributes().stream()
                                .collect(Collectors.toMap(
                                        ProductAttribute::getAttributeName,
                                        ProductAttribute::getAttributeValue,
                                        (a, b) -> a));
                        variants.add(ProductVariantDto.builder()
                                .id(defaultVariant.getId())
                                .sku(child.getSku())
                                .price(defaultVariant.getPrice() != null ? defaultVariant.getPrice() : child.getPrice())
                                .wholesalePrice(defaultVariant.getWholesalePrice() != null ? defaultVariant.getWholesalePrice() : child.getWholesalePrice())
                                .stockQuantity(defaultVariant.getStockQuantity())
                                .attributes(attrs.isEmpty() ? null : attrs)
                                .isActive(child.getIsActive())
                                .externalId(defaultVariant.getExternalId())
                                .build());
                    });
        }

        ProductResponse.ProductResponseBuilder builder = ProductResponse.builder()
                .id(product.getId())
                .name(product.getName())
                .slug(product.getSlug())
                .description(product.getDescription())
                .shortDescription(product.getShortDescription())
                .material(product.getMaterial())
                .price(product.getPrice())
                .wholesalePrice(product.getWholesalePrice())
                .stockQuantity(product.getStockQuantity())
                .isActive(product.getIsActive())
                .isFeatured(product.getIsFeatured())
                .externalId(product.getExternalId())
                .sku(product.getSku())
                .externalCode(product.getExternalCode())
                .unitOfMeasure(product.getUnitOfMeasure())
                .vatRate(product.getVatRate())
                .isVariantChild(product.getIsVariantChild())
                .parentProductId(product.getParentProductId())
                .createdAt(product.getCreatedAt())
                .updatedAt(product.getUpdatedAt())
                .source(product.getSource())
                .variants(variants)
                .images(product.getImages().stream().map(ImageMapper::mapToResponse).toList())
                .videos(product.getVideos().stream().map(VideoMapper::mapToResponse).toList())
                .attributes(product.getAttributes().stream().map(AttributeMapper::mapToResponse).toList());

        if (product.getCategory() != null) {
            builder.categoryId(product.getCategory().getId());
            builder.categoryName(product.getCategory().getName());
        }

        return builder.build();
    }

    public static ProductVariantDto mapVariantToDto(ProductVariant variant) {
        return ProductVariantDto.builder()
                .id(variant.getId())
                .sku(variant.getSku())
                .price(variant.getPrice())
                .wholesalePrice(variant.getWholesalePrice())
                .stockQuantity(variant.getStockQuantity())
                .attributes(variant.getAttributes())
                .isActive(variant.getIsActive())
                .externalId(variant.getExternalId())
                .build();
    }
}
