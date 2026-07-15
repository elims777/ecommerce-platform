package ru.rfsnab.productservice.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.rfsnab.productservice.dto.FacetDto;
import ru.rfsnab.productservice.repository.CategoryRepository;
import ru.rfsnab.productservice.repository.ProductAttributeRepository;
import ru.rfsnab.productservice.repository.ProductRepository;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductFacetTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private CategoryService categoryService;

    @Mock
    private SlugGeneratorService slugGenerator;

    @Mock
    private ProductAttributeRepository attributeRepository;

    @InjectMocks
    private ProductService productService;

    @Test
    void groupsAttributeRowsIntoFacets() {
        when(categoryRepository.existsById(5L)).thenReturn(true);
        when(categoryService.getSubtreeCategoryIds(5L)).thenReturn(List.of(5L, 6L));
        when(attributeRepository.findFacetRows(eq(List.of(5L, 6L)), anySet())).thenReturn(List.of(
                new Object[]{"Состав ткани", "100% хлопок"},
                new Object[]{"Состав ткани", "65% полиэфир / 35% хлопок"},
                new Object[]{"Защитные свойства", "Ми"}
        ));

        List<FacetDto> facets = productService.getFacets(5L);

        assertThat(facets).hasSize(2);
        assertThat(facets).anySatisfy(f -> {
            assertThat(f.name()).isEqualTo("Состав ткани");
            assertThat(f.values()).containsExactly("100% хлопок", "65% полиэфир / 35% хлопок");
        });
        assertThat(facets).anySatisfy(f -> {
            assertThat(f.name()).isEqualTo("Защитные свойства");
            assertThat(f.values()).containsExactly("Ми");
        });
    }

    @Test
    void parsesAttrParamsIntoMap() {
        Map<String, List<String>> parsed = ProductService.parseAttrFilters(List.of(
                "Состав ткани:100% хлопок",
                "Состав ткани:65% полиэфир / 35% хлопок",
                "Защитные свойства:Ми"));
        assertThat(parsed).containsOnlyKeys("Состав ткани", "Защитные свойства");
        assertThat(parsed.get("Состав ткани"))
                .containsExactlyInAnyOrder("100% хлопок", "65% полиэфир / 35% хлопок");
        assertThat(parsed.get("Защитные свойства")).containsExactly("Ми");
    }
}
