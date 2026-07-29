package ru.rfsnab.productservice.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.rfsnab.productservice.model.Product;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SalePriceCalculator — расчёт акционной цены")
class SalePriceCalculatorTest {

    private static Product product(Boolean isSale, String markupPercent) {
        return Product.builder()
                .isSale(isSale)
                .saleMarkupPercent(markupPercent != null ? new BigDecimal(markupPercent) : null)
                .build();
    }

    @Test
    @DisplayName("наценка +10% увеличивает цену")
    void positiveMarkup_IncreasesPrice() {
        assertThat(SalePriceCalculator.apply(new BigDecimal("100.00"), new BigDecimal("10")))
                .isEqualByComparingTo("110.00");
    }

    @Test
    @DisplayName("отрицательный процент работает как скидка")
    void negativeMarkup_ActsAsDiscount() {
        assertThat(SalePriceCalculator.apply(new BigDecimal("100.00"), new BigDecimal("-15")))
                .isEqualByComparingTo("85.00");
    }

    @Test
    @DisplayName("дробный процент округляется до копеек (HALF_UP)")
    void fractionalMarkup_RoundedToKopecks() {
        assertThat(SalePriceCalculator.apply(new BigDecimal("99.99"), new BigDecimal("7.5")))
                .isEqualByComparingTo("107.49");
    }

    @Test
    @DisplayName("без процента цена не меняется")
    void nullMarkup_PriceUnchanged() {
        assertThat(SalePriceCalculator.apply(new BigDecimal("100.00"), null))
                .isEqualByComparingTo("100.00");
    }

    @Test
    @DisplayName("нулевой процент цену не меняет")
    void zeroMarkup_PriceUnchanged() {
        assertThat(SalePriceCalculator.apply(new BigDecimal("100.00"), BigDecimal.ZERO))
                .isEqualByComparingTo("100.00");
    }

    @Test
    @DisplayName("цена null остаётся null (товар «Уточнить стоимость»)")
    void nullPrice_StaysNull() {
        assertThat(SalePriceCalculator.apply(null, new BigDecimal("10"))).isNull();
    }

    @Test
    @DisplayName("метка товара перебивает акцию категории")
    void productMarkup_OverridesCategory() {
        BigDecimal markup = SalePriceCalculator.resolveMarkup(
                product(true, "-15"), new BigDecimal("10"));

        assertThat(markup).isEqualByComparingTo("-15");
    }

    @Test
    @DisplayName("без метки товара берётся процент категории")
    void withoutProductMarkup_CategoryApplies() {
        BigDecimal markup = SalePriceCalculator.resolveMarkup(
                product(false, null), new BigDecimal("10"));

        assertThat(markup).isEqualByComparingTo("10");
    }

    @Test
    @DisplayName("метка товара без процента не перебивает категорию")
    void productFlagWithoutPercent_FallsBackToCategory() {
        BigDecimal markup = SalePriceCalculator.resolveMarkup(
                product(true, null), new BigDecimal("10"));

        assertThat(markup).isEqualByComparingTo("10");
    }

    @Test
    @DisplayName("нет ни меток товара, ни категории — акции нет")
    void noMarkupsAtAll_ReturnsNull() {
        assertThat(SalePriceCalculator.resolveMarkup(product(false, null), null)).isNull();
    }
}
