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
    /** Акционная цена, если товар участвует в акции; иначе базовая. */
    private BigDecimal price;
    private BigDecimal wholesalePrice;

    /** Базовая цена до акции — заполняется только когда акция реально изменила цену. */
    private BigDecimal oldPrice;
    private BigDecimal oldWholesalePrice;
    /** Товар участвует в акции: своя метка ИЛИ метка категории (эффективное значение для витрины). */
    private Boolean isSale;
    /** Действующий процент акции: положительный — наценка, отрицательный — скидка. */
    private BigDecimal saleMarkupPercent;

    /**
     * Собственная метка товара, без учёта категории — только для админки.
     * Форма редактирования обязана заполняться этими полями, иначе унаследованный
     * от категории процент при сохранении «прилипнет» к товару.
     */
    private Boolean ownSale;
    private BigDecimal ownSaleMarkupPercent;

    private Integer stockQuantity;
    private Long categoryId;
    private String categoryName;  // для удобства фронтенда
    private String categoryExternalId;
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
    private Boolean hasVariants;
    private Integer displayOrder;

    // Вложенные данные
    private List<ProductResponse> children;
    private List<ProductImageResponse> images;
    private List<ProductVideoResponse> videos;
    private List<ProductAttributeResponse> attributes;
    private List<ProductDocumentDto> documents;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
