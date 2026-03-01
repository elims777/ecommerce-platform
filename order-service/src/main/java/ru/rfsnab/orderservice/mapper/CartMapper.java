package ru.rfsnab.orderservice.mapper;

import ru.rfsnab.orderservice.models.dto.cart.CartDto;
import ru.rfsnab.orderservice.models.dto.cart.CartItemDto;
import ru.rfsnab.orderservice.models.dto.product.ProductDto;
import ru.rfsnab.orderservice.models.entity.Cart;
import ru.rfsnab.orderservice.models.entity.CartItem;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Mapper для преобразования Cart entity в CartDto.
 * Чистый маппинг без внешних зависимостей.
 */
public class CartMapper {

    public static CartDto toDto(Cart cart, Map<Long, ProductDto> products) {
        if (cart.getItems().isEmpty()) {
            return emptyCartDto(cart.getUserId());
        }

        List<CartItemDto> items = cart.getItems().stream()
                .filter(item -> products.containsKey(item.getProductId()))
                .map(item -> toItemDto(item, products.get(item.getProductId())))
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

    private static CartItemDto toItemDto(CartItem item, ProductDto product) {
        BigDecimal subtotal = product.price()
                .multiply(BigDecimal.valueOf(item.getQuantity()));

        return new CartItemDto(
                product.id(),
                product.name(),
                item.getQuantity(),
                product.price(),
                subtotal
        );
    }
}
