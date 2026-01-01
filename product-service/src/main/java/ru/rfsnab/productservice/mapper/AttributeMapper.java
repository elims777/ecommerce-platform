package ru.rfsnab.productservice.mapper;

import ru.rfsnab.productservice.dto.ProductAttributeRequest;
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

    public static ProductAttribute toEntity(ProductAttributeRequest request) {
        return ProductAttribute.builder()
                .attributeName(request.getAttributeName())
                .attributeValue(request.getAttributeValue())
                .build();
    }
}
