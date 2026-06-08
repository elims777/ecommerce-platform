package ru.rfsnab.productservice.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

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

    // Полное описание (из ФТК — колонка F)
    private String description;

    // Краткое описание из 1С
    @Size(max = 1000)
    private String shortDescription;


    @DecimalMin(value = "0.0")
    private BigDecimal price;

    @DecimalMin(value = "0.0")
    private BigDecimal wholesalePrice;

    @Min(value = 0)
    private Integer stockQuantity;

    // Основной материал (из ФТК)
    @Size(max = 500)
    private String material;

    // Единица измерения
    private String unitOfMeasure;

    // Ставка НДС
    private Integer vatRate;

    // Источник товара: INTERNAL (1С), FTK и т.д.
    private String source;

    private List<ProductAttributeImportItem> attributes;

    // Варианты (размер/цвет/рост) — если пусто, используется default-вариант
    private List<VariantImportItem> variants;

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

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class VariantImportItem {
        // Уникальный идентификатор варианта у источника (артикул.001 у ФТК)
        @NotBlank
        private String externalId;
        private String sku;
        private BigDecimal price;
        private BigDecimal wholesalePrice;
        private Integer stockQuantity;
        // {"Размер": "XL", "Рост": "170-176"}
        private Map<String, String> attributes;
    }
}
