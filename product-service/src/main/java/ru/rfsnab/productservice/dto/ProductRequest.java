package ru.rfsnab.productservice.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductRequest {
    @NotBlank(message = "Название товара обязательно")
    @Size(min = 3, max = 255, message = "Название должно быть от 3 до 255 символов")
    private String name;

    @Size(max = 5000, message = "Описание не должно превышать 5000 символов")
    private String description;

    @Size(max = 1000, message = "Краткое описание не должно превышать 1000 символов")
    private String shortDescription;

    @DecimalMin(value = "0.0", message = "Цена не может быть отрицательной")
    private BigDecimal price;

    @Min(value = 0, message = "Количество не может быть отрицательным")
    @Builder.Default
    private Integer stockQuantity = 0;

    private Long categoryId;  // может быть null

    @Builder.Default
    private Boolean isActive = true;

    @Builder.Default
    private Boolean isFeatured = false;

    private String externalId;
    private String sku;
    private String externalCode;
    private String unitOfMeasure;
    private Integer vatRate;
}
