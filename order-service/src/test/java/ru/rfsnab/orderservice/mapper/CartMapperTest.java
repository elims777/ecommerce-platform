package ru.rfsnab.orderservice.mapper;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import ru.rfsnab.orderservice.models.dto.cart.CartDto;
import ru.rfsnab.orderservice.models.dto.cart.CartItemDto;
import ru.rfsnab.orderservice.models.dto.product.ProductDto;
import ru.rfsnab.orderservice.models.entity.Cart;
import ru.rfsnab.orderservice.models.entity.CartItem;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("CartMapper")
class CartMapperTest {

    private static final Long USER_ID = 100L;

    // ==================== toDto ====================

    @Nested
    @DisplayName("toDto")
    class ToDtoTests {

        @Test
        @DisplayName("маппит корзину с товарами в CartDto с расчётом итогов")
        void shouldMapCartWithItemsToDto() {
            Cart cart = buildCartWithItems();
            Map<Long, ProductDto> products = Map.of(
                    1L, new ProductDto(1L, "Доска обрезная", new BigDecimal("1500.00"), 100, true),
                    2L, new ProductDto(2L, "Брус 100x100", new BigDecimal("3200.00"), 50, true)
            );

            CartDto dto = CartMapper.toDto(cart, products);

            assertThat(dto.userId()).isEqualTo(USER_ID);
            assertThat(dto.items()).hasSize(2);
            assertThat(dto.totalItems()).isEqualTo(15); // 10 + 5
            assertThat(dto.totalAmount()).isEqualByComparingTo("31000.00"); // 10*1500 + 5*3200
        }

        @Test
        @DisplayName("корректно рассчитывает subtotal для каждой позиции")
        void shouldCalculateSubtotalPerItem() {
            Cart cart = buildCartWithSingleItem(1L, 10);
            Map<Long, ProductDto> products = Map.of(
                    1L, new ProductDto(1L, "Доска обрезная", new BigDecimal("1500.00"), 100, true)
            );

            CartDto dto = CartMapper.toDto(cart, products);

            CartItemDto item = dto.items().getFirst();
            assertThat(item.productId()).isEqualTo(1L);
            assertThat(item.productName()).isEqualTo("Доска обрезная");
            assertThat(item.quantity()).isEqualTo(10);
            assertThat(item.price()).isEqualByComparingTo("1500.00");
            assertThat(item.subtotal()).isEqualByComparingTo("15000.00");
        }

        @Test
        @DisplayName("пропускает товары, отсутствующие в products map (удалены/деактивированы)")
        void shouldFilterOutMissingProducts() {
            Cart cart = buildCartWithItems(); // productId 1L и 2L
            // Только один товар в map — второй "удалён"
            Map<Long, ProductDto> products = Map.of(
                    1L, new ProductDto(1L, "Доска обрезная", new BigDecimal("1500.00"), 100, true)
            );

            CartDto dto = CartMapper.toDto(cart, products);

            assertThat(dto.items()).hasSize(1);
            assertThat(dto.items().getFirst().productId()).isEqualTo(1L);
            assertThat(dto.totalItems()).isEqualTo(10);
            assertThat(dto.totalAmount()).isEqualByComparingTo("15000.00");
        }

        @Test
        @DisplayName("пустая корзина — возвращает emptyCartDto")
        void shouldReturnEmptyDtoForEmptyCart() {
            Cart cart = emptyCart();

            CartDto dto = CartMapper.toDto(cart, Map.of());

            assertThat(dto.userId()).isEqualTo(USER_ID);
            assertThat(dto.items()).isEmpty();
            assertThat(dto.totalItems()).isZero();
            assertThat(dto.totalAmount()).isEqualByComparingTo("0");
        }

        @Test
        @DisplayName("все товары отсутствуют в products map — пустой список items, нулевые итоги")
        void shouldReturnEmptyItemsWhenAllProductsMissing() {
            Cart cart = buildCartWithSingleItem(999L, 5);

            CartDto dto = CartMapper.toDto(cart, Map.of());

            assertThat(dto.items()).isEmpty();
            assertThat(dto.totalItems()).isZero();
            assertThat(dto.totalAmount()).isEqualByComparingTo("0");
        }
    }

    // ==================== emptyCartDto ====================

    @Nested
    @DisplayName("emptyCartDto")
    class EmptyCartDtoTests {

        @Test
        @DisplayName("создаёт пустой CartDto с корректным userId")
        void shouldCreateEmptyCartDto() {
            CartDto dto = CartMapper.emptyCartDto(USER_ID);

            assertThat(dto.userId()).isEqualTo(USER_ID);
            assertThat(dto.items()).isEmpty();
            assertThat(dto.totalItems()).isZero();
            assertThat(dto.totalAmount()).isEqualByComparingTo("0");
        }
    }

    // ==================== Test data builders ====================

    private Cart buildCartWithItems() {
        Cart cart = Cart.builder().userId(USER_ID).build();

        CartItem item1 = CartItem.builder()
                .cart(cart).productId(1L).quantity(10).build();
        CartItem item2 = CartItem.builder()
                .cart(cart).productId(2L).quantity(5).build();

        cart.getItems().addAll(List.of(item1, item2));
        return cart;
    }

    private Cart buildCartWithSingleItem(Long productId, int quantity) {
        Cart cart = Cart.builder().userId(USER_ID).build();

        CartItem item = CartItem.builder()
                .cart(cart).productId(productId).quantity(quantity).build();
        cart.getItems().add(item);

        return cart;
    }

    private Cart emptyCart() {
        return Cart.builder().userId(USER_ID).build();
    }
}
