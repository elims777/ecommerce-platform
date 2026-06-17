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
    private String description;
    private String shortDescription;
    private String material;
    private String unitOfMeasure;
    private BigDecimal price;
    private BigDecimal wholesalePrice;
    private Integer stockQuantity;
    private Integer vatRate;
    private String source;
    private Long categoryId;
    private String barcode;
    private String countryOfOrigin;

    /** Пути к изображениям (относительные, от goods/1/) — для скачивания в FtkImportService. */
    private List<String> imagePaths;

    /** Расшифрованные свойства из классификатора: имя → значение. */
    private Map<String, String> properties;

    /** Варианты — дочерние Product записи. */
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
        private String barcode;
        private String countryOfOrigin;
        private Map<String, String> attributes;
    }
}
