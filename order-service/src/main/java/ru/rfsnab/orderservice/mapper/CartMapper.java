package ru.rfsnab.orderservice.mapper;

import ru.rfsnab.orderservice.models.dto.cart.CartDto;
import ru.rfsnab.orderservice.models.dto.cart.CartItemDto;
import ru.rfsnab.orderservice.models.dto.product.ProductDto;
import ru.rfsnab.orderservice.models.entity.Cart;
import ru.rfsnab.orderservice.models.entity.CartItem;
import ru.rfsnab.orderservice.models.entity.enums.CustomerType;
import ru.rfsnab.orderservice.service.PriceSelector;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public class CartMapper {

    public static CartDto toDto(Cart cart, Map<Long, ProductDto> products, CustomerType customerType) {
        if (cart.getItems().isEmpty()) {
            return emptyCartDto(cart.getUserId());
        }

        List<CartItemDto> items = cart.getItems().stream()
                .filter(item -> products.containsKey(item.getProductId()))
                .map(item -> toItemDto(item, products.get(item.getProductId()), customerType))
                .toList();

        int totalItems = items.stream()
                .mapToInt(CartItemDto::quantity)
                .sum();

        BigDecimal totalAmount = items.stream()
                .map(CartItemDto::subtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new CartDto(cart.getUserId(), items, totalItems, totalAmount);
    }

    public static CartDto emptyCartDto(Long userId) {
        return new CartDto(userId, List.of(), 0, BigDecimal.ZERO);
    }

    private static CartItemDto toItemDto(CartItem item, ProductDto product, CustomerType customerType) {
        BigDecimal price = PriceSelector.pickPrice(product, customerType);
        BigDecimal subtotal = price.multiply(BigDecimal.valueOf(item.getQuantity()));

        return new CartItemDto(
                product.id(),
                product.name(),
                item.getQuantity(),
                price,
                subtotal,
                product.parentProductId()
        );
    }
}
