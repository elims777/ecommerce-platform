package ru.rfsnab.integrationservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * DTO элемента импорта — зеркало ProductImportItem из product-service.
 * Формируется из CmlProduct + Offer (CommerceML) при маппинге.
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
    private Integer stock;
    private BigDecimal vatRate;
}
