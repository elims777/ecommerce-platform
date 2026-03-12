package ru.rfsnab.productservice.dto;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CategoryResponse {

    private Long id;
    private String name;
    private String slug;
    private String description;
    private Long parentId;
    private String parentName;
    private Boolean isActive;
    private Integer displayOrder;
    private String externalId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
