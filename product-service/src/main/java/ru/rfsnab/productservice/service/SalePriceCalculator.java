package ru.rfsnab.productservice.service;

import ru.rfsnab.productservice.model.Product;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Расчёт акционной цены на лету. Цены в БД остаются базовыми — процент применяется
 * только при выдаче, поэтому снятие метки сразу возвращает исходную цену.
 * <p>
 * Положительный процент — наценка, отрицательный — скидка.
 * Метка на товаре перебивает акцию его категории.
 */
public final class SalePriceCalculator {

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private SalePriceCalculator() {}

    /**
     * Действующий процент для товара: собственная метка, иначе — унаследованная от категории.
     *
     * @param categoryMarkup процент категории товара (уже с учётом наследования), может быть null
     * @return null, если товар не участвует в акции
     */
    public static BigDecimal resolveMarkup(Product product, BigDecimal categoryMarkup) {
        if (Boolean.TRUE.equals(product.getIsSale()) && product.getSaleMarkupPercent() != null) {
            return product.getSaleMarkupPercent();
        }
        return categoryMarkup;
    }

    /**
     * Применяет процент к цене. Возвращает исходное значение, если применять нечего.
     */
    public static BigDecimal apply(BigDecimal price, BigDecimal markupPercent) {
        if (price == null || markupPercent == null || markupPercent.signum() == 0) {
            return price;
        }
        BigDecimal multiplier = BigDecimal.ONE.add(markupPercent.divide(HUNDRED, 4, RoundingMode.HALF_UP));
        return price.multiply(multiplier).setScale(2, RoundingMode.HALF_UP);
    }
}
