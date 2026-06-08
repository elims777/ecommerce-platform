package ru.rfsnab.productservice.dto;

import lombok.*;

import java.math.BigDecimal;
import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductVariantDto {
    private Long id;
    private String sku;
    private BigDecimal price;
    private BigDecimal wholesalePrice;
    private Integer stockQuantity;
    private Map<String, String> attributes;
    private Boolean isActive;
    private String externalId;
}
