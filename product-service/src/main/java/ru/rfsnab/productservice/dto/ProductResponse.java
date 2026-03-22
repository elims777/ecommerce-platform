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
    private Integer stockQuantity;
    private Long categoryId;
    private String categoryName;  // для удобства фронтенда
    private Boolean isActive;
    private Boolean isFeatured;

    //интеграция с 1с
    private String externalId;
    private String sku;
    private String externalCode;
    private String unitOfMeasure;
    private Integer vatRate;

    // Вложенные данные
    private List<ProductImageResponse> images;
    private List<ProductVideoResponse> videos;
    private List<ProductAttributeResponse> attributes;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
