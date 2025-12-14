package ru.rfsnab.productservice.mapper;

import ru.rfsnab.productservice.dto.ProductAttributeResponse;
import ru.rfsnab.productservice.model.ProductAttribute;

public class AttributeMapper {

    public static ProductAttributeResponse mapToResponse(ProductAttribute attribute){
        return ProductAttributeResponse.builder()
                .id(attribute.getId())
                .attributeName(attribute.getAttributeName())
                .attributeValue(attribute.getAttributeValue())
                .build();
    }
}
