package ru.rfsnab.productservice.mapper;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.rfsnab.productservice.dto.ProductResponse;
import ru.rfsnab.productservice.model.Category;
import ru.rfsnab.productservice.model.Product;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ProductMapper — акционные цены в ответе")
class ProductMapperSaleTest {

    private static Product product() {
        return Product.builder()
                .id(1L)
                .name("Ботинки")
                .price(new BigDecimal("100.00"))
                .wholesalePrice(new BigDecimal("120.00"))
                .category(Category.builder().id(54L).name("Спецобувь").build())
                .build();
    }

    @Test
    @DisplayName("наценка категории: цены выше базовых, но зачёркивать нечего")
    void categoryMarkup_ReturnsRaisedPricesWithoutOldOnes() {
        ProductResponse response = ProductMapper.mapWithSale(product(), new BigDecimal("10"));

        assertThat(response.getPrice()).isEqualByComparingTo("110.00");
        assertThat(response.getWholesalePrice()).isEqualByComparingTo("132.00");
        // Старая цена была бы НИЖЕ текущей — зачёркивание выглядело бы абсурдом
        assertThat(response.getOldPrice()).isNull();
        assertThat(response.getOldWholesalePrice()).isNull();
        assertThat(response.getIsSale()).isTrue();
        assertThat(response.getSaleMarkupPercent()).isEqualByComparingTo("10");
    }

    @Test
    @DisplayName("скидка категории: базовые цены уходят в oldPrice")
    void categoryDiscount_ReturnsSalePricesWithOldOnes() {
        ProductResponse response = ProductMapper.mapWithSale(product(), new BigDecimal("-20"));

        assertThat(response.getPrice()).isEqualByComparingTo("80.00");
        assertThat(response.getWholesalePrice()).isEqualByComparingTo("96.00");
        assertThat(response.getOldPrice()).isEqualByComparingTo("100.00");
        assertThat(response.getOldWholesalePrice()).isEqualByComparingTo("120.00");
        assertThat(response.getIsSale()).isTrue();
    }

    @Test
    @DisplayName("скидка на товаре перебивает наценку категории")
    void productDiscount_OverridesCategoryMarkup() {
        Product product = product();
        product.setIsSale(true);
        product.setSaleMarkupPercent(new BigDecimal("-15"));

        ProductResponse response = ProductMapper.mapWithSale(product, new BigDecimal("10"));

        assertThat(response.getPrice()).isEqualByComparingTo("85.00");
        assertThat(response.getOldPrice()).isEqualByComparingTo("100.00");
    }

    @Test
    @DisplayName("без акции цены базовые, oldPrice не заполняется")
    void noSale_ReturnsBasePricesWithoutOldPrice() {
        ProductResponse response = ProductMapper.mapWithSale(product(), null);

        assertThat(response.getPrice()).isEqualByComparingTo("100.00");
        assertThat(response.getWholesalePrice()).isEqualByComparingTo("120.00");
        assertThat(response.getOldPrice()).isNull();
        assertThat(response.getOldWholesalePrice()).isNull();
        assertThat(response.getIsSale()).isFalse();
    }

    @Test
    @DisplayName("нулевой процент: акция есть, но зачёркивать нечего")
    void zeroMarkup_NoOldPrice() {
        ProductResponse response = ProductMapper.mapWithSale(product(), BigDecimal.ZERO);

        assertThat(response.getPrice()).isEqualByComparingTo("100.00");
        assertThat(response.getOldPrice()).isNull();
    }
}
