package ru.rfsnab.productservice.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductVideoResponse {
    private Long id;
    private String videoUrl;
    private String videoType;
    private String provider;
    private String thumbnailUrl;
    private Integer durationSeconds;
    private Boolean isPrimary;
    private Integer displayOrder;
    private String title;
}
