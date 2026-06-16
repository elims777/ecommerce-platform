package ru.rfsnab.productservice.dto;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductResponse {
    private Long id;
    private String name;
    private String slug;
    private String description;
    private String shortDescription;
    private BigDecimal price;
    private BigDecimal wholesalePrice;
    private Integer stockQuantity;
    private Long categoryId;
    private String categoryName;  // для удобства фронтенда
    private Boolean isActive;
    private Boolean isFeatured;

    private String material;
    private String barcode;
    private String countryOfOrigin;

    //интеграция с 1с
    private String externalId;
    private String sku;
    private String externalCode;
    private String unitOfMeasure;
    private Integer vatRate;

    private String source;

    private Boolean isVariantChild;
    private Long parentProductId;

    // Вложенные данные
    private List<ProductResponse> children;
    private List<ProductImageResponse> images;
    private List<ProductVideoResponse> videos;
    private List<ProductAttributeResponse> attributes;
    private List<ProductDocumentDto> documents;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
