package ru.rfsnab.productservice.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductDocumentDto {
    private Long id;
    private String name;
    private String url;
    private String contentType;
    private Integer displayOrder;
}
