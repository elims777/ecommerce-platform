package ru.rfsnab.productservice.mapper;

import ru.rfsnab.productservice.dto.ProductImageResponse;
import ru.rfsnab.productservice.model.ProductImage;

public class ImageMapper {

    public static ProductImageResponse mapToResponse(ProductImage image){
        return ProductImageResponse.builder()
                .id(image.getId())
                .fileUrl(image.getFileUrl())
                .fileSize(image.getFileSize())
                .contentType(image.getContentType())
                .width(image.getWidth())
                .height(image.getHeight())
                .isPrimary(image.getIsPrimary())
                .displayOrder(image.getDisplayOrder())
                .altText(image.getAltText())
                .build();
    }
}
