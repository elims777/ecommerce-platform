package ru.rfsnab.productservice.dto;

import lombok.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CategoryTreeDTO {
    private Long id;
    private String name;
    private String slug;
    private String description;
    private Long parentId;
    private Boolean isActive;
    private Integer displayOrder;

    /** Собственная метка категории: акция включена именно здесь */
    private Boolean isSale;

    /** Действующий процент: собственный или унаследованный от предка */
    private BigDecimal saleMarkupPercent;

    /** Акция действует, но включена у предка (для приглушённого бейджа в админке) */
    private Boolean inheritedSale;

    @Builder.Default
    private List<CategoryTreeDTO> children = new ArrayList<>();

    public void addChild(CategoryTreeDTO child) {
        children.add(child);
    }
}
