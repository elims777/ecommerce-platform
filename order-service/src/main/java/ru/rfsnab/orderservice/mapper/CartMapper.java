package ru.rfsnab.orderservice.mapper;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.rfsnab.orderservice.models.dto.cart.CartDto;
import ru.rfsnab.orderservice.models.dto.cart.CartItemDto;
import ru.rfsnab.orderservice.models.dto.product.ProductDto;
import ru.rfsnab.orderservice.models.entity.Cart;
import ru.rfsnab.orderservice.models.entity.CartItem;
import ru.rfsnab.orderservice.service.client.ProductServiceClient;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Mapper для преобразования Cart entity в CartDto.
 * Обогащает данные информацией о товарах из product-service.
 */
@Component
@RequiredArgsConstructor
public class CartMapper {
    private final ProductServiceClient productServiceClient;

    /**
     * Преобразование Cart entity в CartDto с обогащением данными о товарах.
     *
     * @param cart корзина
     * @return DTO с полной информацией о товарах (название, цена, subtotal)
     */
    public CartDto toDto(Cart cart) {
        if (cart.getItems().isEmpty()) {
            return new CartDto(cart.getUserId(), List.of(), 0, BigDecimal.ZERO);
        }

        // Собираем ID всех товаров
        Set<Long> productIds = cart.getItems().stream()
                .map(CartItem::getProductId)
                .collect(Collectors.toSet());

        // Получаем данные о товарах из product-service
        Map<Long, ProductDto> products = productServiceClient.getProducts(productIds);

        // Мапим позиции корзины с обогащением
        List<CartItemDto> items = cart.getItems().stream()
                .filter(item -> products.containsKey(item.getProductId()))
                .map(item -> toItemDto(item, products.get(item.getProductId())))
                .toList();

        // Считаем итоги
        int totalItems = items.stream()
                .mapToInt(CartItemDto::quantity)
                .sum();

        BigDecimal totalAmount = items.stream()
                .map(CartItemDto::subtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new CartDto(cart.getUserId(), items, totalItems, totalAmount);
    }

    /**
     * Преобразование CartItem в CartItemDto с данными о товаре.
     */
    private CartItemDto toItemDto(CartItem item, ProductDto product) {
        BigDecimal subtotal = product.price().multiply(BigDecimal.valueOf(item.getQuantity()));

        return new CartItemDto(
                product.id(),
                product.name(),
                item.getQuantity(),
                product.price(),
                subtotal
        );
    }
}
