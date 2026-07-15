package ru.rfsnab.productservice.mapper;

import org.springframework.data.domain.Page;
import ru.rfsnab.productservice.dto.ProductDocumentDto;
import ru.rfsnab.productservice.dto.ProductRequest;
import ru.rfsnab.productservice.dto.ProductResponse;
import ru.rfsnab.productservice.model.Category;
import ru.rfsnab.productservice.model.Product;
import ru.rfsnab.productservice.model.ProductDocument;
import ru.rfsnab.productservice.service.ProductAttributeExclusions;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class ProductMapper {

    public static Product mapToEntity(ProductRequest productRequest) {
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
        List<ProductResponse> childResponses = children.stream()
                .map(ProductMapper::mapToResponse)
                .collect(Collectors.toList());

        ProductResponse.ProductResponseBuilder builder = ProductResponse.builder()
                .id(product.getId())
                .name(product.getName())
                .slug(product.getSlug())
                .description(product.getDescription())
                .shortDescription(product.getShortDescription())
                .material(product.getMaterial())
                .barcode(product.getBarcode())
                .countryOfOrigin(product.getCountryOfOrigin())
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
                .displayOrder(product.getDisplayOrder())
                .createdAt(product.getCreatedAt())
                .updatedAt(product.getUpdatedAt())
                .source(product.getSource())
                .children(childResponses)
                .images(product.getImages().stream().map(ImageMapper::mapToResponse).toList())
                .videos(product.getVideos().stream().map(VideoMapper::mapToResponse).toList())
                .attributes(product.getAttributes().stream()
                        .filter(a -> !ProductAttributeExclusions.NAMES.contains(a.getAttributeName()))
                        .map(AttributeMapper::mapToResponse).toList())
                .documents(product.getDocuments().stream().map(ProductMapper::mapDocumentToDto).toList());

        if (product.getCategory() != null) {
            builder.categoryId(product.getCategory().getId());
            builder.categoryName(product.getCategory().getName());
            builder.categoryExternalId(product.getCategory().getExternalId());
        }

        return builder.build();
    }

    public static Page<ProductResponse> mapPageWithHasVariants(Page<Product> page, Set<Long> parentIdsWithChildren) {
        return page.map(p -> {
            ProductResponse response = mapToResponse(p);
            response.setHasVariants(parentIdsWithChildren.contains(p.getId()));
            return response;
        });
    }

    public static ProductDocumentDto mapDocumentToDto(ProductDocument document) {
        return ProductDocumentDto.builder()
                .id(document.getId())
                .name(document.getName())
                .url(document.getUrl())
                .contentType(document.getContentType())
                .displayOrder(document.getDisplayOrder())
                .build();
    }
}
