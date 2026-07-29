package ru.rfsnab.productservice.dto;

import lombok.*;

import java.math.BigDecimal;
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
    /** Акционная категория: процент применяется ко всем товарам её поддерева. */
    private Boolean isSale;
    private BigDecimal saleMarkupPercent;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
