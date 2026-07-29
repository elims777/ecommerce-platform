package ru.rfsnab.productservice.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CategoryRequest {

    @NotBlank(message = "Название категории обязательно")
    @Size(min = 2, max = 255, message = "Название должно быть от 2 до 255 символов")
    private String name;

    @Size(max = 5000, message = "Описание не должно превышать 5000 символов")
    private String description;

    private Long parentId;
    private String externalId;
    private Integer displayOrder;

    /** Акционная категория: процент применяется ко всем товарам её поддерева. */
    private Boolean isSale;

    @DecimalMin(value = "-90.0", message = "Скидка не может превышать 90%")
    private BigDecimal saleMarkupPercent;
}
