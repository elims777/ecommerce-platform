package ru.rfsnab.productservice.dto;

import lombok.*;

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

    @Builder.Default
    private List<CategoryTreeDTO> children = new ArrayList<>();

    public void addChild(CategoryTreeDTO child) {
        children.add(child);
    }
}
