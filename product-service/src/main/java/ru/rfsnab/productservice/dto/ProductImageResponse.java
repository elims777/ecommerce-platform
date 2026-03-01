package ru.rfsnab.productservice.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductImageResponse {
    private Long id;
    private String fileUrl;
    private Long fileSize;
    private String contentType;
    private Integer width;
    private Integer height;
    private Boolean isPrimary;
    private Integer displayOrder;
    private String altText;
}
