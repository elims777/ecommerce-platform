package ru.rfsnab.orderservice.service;

import ru.rfsnab.orderservice.models.dto.product.ProductDto;
import ru.rfsnab.orderservice.models.entity.enums.CustomerType;

import java.math.BigDecimal;

/**
 * Выбор цены товара по типу клиента.
 * Инвариант проекта (см. CLAUDE.md): названия колонок исторически инвертированы.
 * B2B → price (оптовая), B2C → wholesalePrice (розничная, fallback на price).
 * Не переименовывать.
 */
public final class PriceSelector {

    private PriceSelector() {}

    public static BigDecimal pickPrice(ProductDto product, CustomerType customerType) {
        if (customerType == CustomerType.B2B) {
            return product.price();
        }
        return product.wholesalePrice() != null ? product.wholesalePrice() : product.price();
    }

    public static CustomerType parseCustomerType(String raw) {
        if (raw == null) return CustomerType.B2C;
        try {
            return CustomerType.valueOf(raw.toUpperCase());
        } catch (IllegalArgumentException e) {
            return CustomerType.B2C;
        }
    }
}
