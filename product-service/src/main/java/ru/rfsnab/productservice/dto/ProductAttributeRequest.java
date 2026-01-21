package ru.rfsnab.productservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductAttributeRequest {
    @NotBlank(message = "Название атрибута обязательно")
    @Size(max = 100, message = "Название не должно превышать 100 символов")
    private String attributeName;

    @NotBlank(message = "Значение атрибута обязательно")
    @Size(max = 500, message = "Значение не должно превышать 500 символов")
    private String attributeValue;
}
