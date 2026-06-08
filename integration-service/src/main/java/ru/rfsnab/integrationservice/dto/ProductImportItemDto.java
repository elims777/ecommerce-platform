package ru.rfsnab.integrationservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * DTO элемента импорта — зеркало ProductImportItem из product-service.
 * Формируется из CmlProduct + Offer (CommerceML) или из FTK XLS.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductImportItemDto {

    private String externalId;
    private String name;
    private String sku;
    private String shortDescription;
    private String unitOfMeasure;
    private BigDecimal price;
    private BigDecimal wholesalePrice;
    private Integer stockQuantity;
    private Integer vatRate;
    // Источник: INTERNAL, FTK, ...
    private String source;
    // Явные варианты (размер/рост/цвет). Если пусто — используется default-вариант
    private List<VariantImportItemDto> variants;

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VariantImportItemDto {
        private String externalId;
        private String sku;
        private BigDecimal price;
        private BigDecimal wholesalePrice;
        private Integer stockQuantity;
        private Map<String, String> attributes;
    }
}
