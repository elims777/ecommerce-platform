package ru.rfsnab.productservice.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductImportItem {
    // UUID товара из 1С (обязательный, ключ матчинга)
    @NotBlank(message = "externalId обязателен для импорта")
    private String externalId;

    // Код номенклатуры 1С (НФ-00003735)
    private String externalCode;

    // Артикул
    private String sku;


    @NotBlank(message = "Название товара обязательно")
    @Size(min = 3, max = 255)
    private String name;

    // Описание из 1С - shortDescription
    @Size(max = 1000)
    private String shortDescription;


    @DecimalMin(value = "0.0")
    private BigDecimal price;


    @Min(value = 0)
    private Integer stockQuantity;

    // Единица измерения
    private String unitOfMeasure;

    // Ставка НДС
    private Integer vatRate;


    private List<ProductAttributeImportItem> attributes;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ProductAttributeImportItem {
        @NotBlank
        private String name;
        @NotBlank
        private String value;
    }
}
