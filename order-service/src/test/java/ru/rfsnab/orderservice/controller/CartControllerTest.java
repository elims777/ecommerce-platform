package ru.rfsnab.orderservice.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import ru.rfsnab.orderservice.BaseIntegrationTest;
import ru.rfsnab.orderservice.exception.InsufficientStockException;
import ru.rfsnab.orderservice.exception.ProductNotFoundException;
import ru.rfsnab.orderservice.models.dto.product.ProductDto;
import ru.rfsnab.orderservice.models.entity.Cart;
import ru.rfsnab.orderservice.models.entity.CartItem;
import ru.rfsnab.orderservice.service.CartService;
import ru.rfsnab.orderservice.service.OrderService;
import ru.rfsnab.orderservice.service.WarehousePointService;

import java.math.BigDecimal;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Тесты контроллера корзины.
 * CartService замокан — тестируем HTTP слой: статусы, JSON response, validation.
 * .with(user("100")) — эмуляция JWT authentication.
 */
@AutoConfigureMockMvc
@DisplayName("CartController")
class CartControllerTest extends BaseIntegrationTest {

    @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CartService cartService;

    @MockitoBean
    private OrderService orderService;

    @MockitoBean
    private WarehousePointService warehousePointService;

    private static final Long USER_ID = 100L;
    private static final Long PRODUCT_ID = 1L;

    // ==================== GET /api/v1/cart ====================

    @Nested
    @DisplayName("GET /api/v1/cart — получение корзины")
    class GetCartTests {

        @Test
        @DisplayName("200 OK — корзина с товарами")
        void shouldReturnCartWithItems() throws Exception {
            Cart cart = buildCartWithItem(PRODUCT_ID, 10);
            Map<Long, ProductDto> products = Map.of(
                    PRODUCT_ID, buildProduct(PRODUCT_ID, "Доска обрезная", "1500.00"));

            when(cartService.getCart(USER_ID)).thenReturn(cart);
            when(cartService.fetchProductsForCart(cart)).thenReturn(products);

            mockMvc.perform(get("/api/v1/cart")
                            .with(user("100")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.userId").value(100))
                    .andExpect(jsonPath("$.items.length()").value(1))
                    .andExpect(jsonPath("$.items[0].productId").value(1))
                    .andExpect(jsonPath("$.items[0].productName").value("Доска обрезная"))
                    .andExpect(jsonPath("$.items[0].quantity").value(10))
                    .andExpect(jsonPath("$.items[0].price").value(1500.00))
                    .andExpect(jsonPath("$.items[0].subtotal").value(15000.00))
                    .andExpect(jsonPath("$.totalItems").value(10))
                    .andExpect(jsonPath("$.totalAmount").value(15000.00));
        }

        @Test
        @DisplayName("200 OK — пустая корзина")
        void shouldReturnEmptyCart() throws Exception {
            Cart cart = emptyCart();
            when(cartService.getCart(USER_ID)).thenReturn(cart);

            mockMvc.perform(get("/api/v1/cart")
                            .with(user("100")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.userId").value(100))
                    .andExpect(jsonPath("$.items").isEmpty())
                    .andExpect(jsonPath("$.totalItems").value(0))
                    .andExpect(jsonPath("$.totalAmount").value(0));
        }

        @Test
        @DisplayName("401 Unauthorized — без авторизации")
        void shouldReturn401WhenNotAuthenticated() throws Exception {
            mockMvc.perform(get("/api/v1/cart"))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ==================== POST /api/v1/cart/items ====================

    @Nested
    @DisplayName("POST /api/v1/cart/items — добавление товара")
    class AddItemTests {

        @Test
        @DisplayName("200 OK — товар добавлен в корзину")
        void shouldAddItemToCart() throws Exception {
            Cart cart = buildCartWithItem(PRODUCT_ID, 5);
            Map<Long, ProductDto> products = Map.of(
                    PRODUCT_ID, buildProduct(PRODUCT_ID, "Доска обрезная", "1500.00"));

            when(cartService.addItemToCart(USER_ID, PRODUCT_ID, 5)).thenReturn(cart);
            when(cartService.fetchProductsForCart(cart)).thenReturn(products);

            String json = """
                    {
                        "productId": 1,
                        "quantity": 5
                    }
                    """;

            mockMvc.perform(post("/api/v1/cart/items")
                            .with(user("100")).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items[0].quantity").value(5))
                    .andExpect(jsonPath("$.totalItems").value(5));

            verify(cartService).addItemToCart(USER_ID, PRODUCT_ID, 5);
        }

        @Test
        @DisplayName("400 Bad Request — productId отсутствует")
        void shouldReturn400WhenProductIdNull() throws Exception {
            String json = """
                    {
                        "quantity": 5
                    }
                    """;

            mockMvc.perform(post("/api/v1/cart/items")
                            .with(user("100")).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("400 Bad Request — quantity = 0")
        void shouldReturn400WhenQuantityZero() throws Exception {
            String json = """
                    {
                        "productId": 1,
                        "quantity": 0
                    }
                    """;

            mockMvc.perform(post("/api/v1/cart/items")
                            .with(user("100")).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("400 Bad Request — отрицательный quantity")
        void shouldReturn400WhenQuantityNegative() throws Exception {
            String json = """
                    {
                        "productId": 1,
                        "quantity": -3
                    }
                    """;

            mockMvc.perform(post("/api/v1/cart/items")
                            .with(user("100")).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("404 Not Found — товар не найден")
        void shouldReturn404WhenProductNotFound() throws Exception {
            when(cartService.addItemToCart(eq(USER_ID), eq(999L), eq(1)))
                    .thenThrow(new ProductNotFoundException("Product is not available: 999"));

            String json = """
                    {
                        "productId": 999,
                        "quantity": 1
                    }
                    """;

            mockMvc.perform(post("/api/v1/cart/items")
                            .with(user("100")).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("409 Conflict — недостаточно товара на складе")
        void shouldReturn409WhenInsufficientStock() throws Exception {
            when(cartService.addItemToCart(eq(USER_ID), eq(PRODUCT_ID), eq(1000)))
                    .thenThrow(new InsufficientStockException("Not enough stock"));

            String json = """
                    {
                        "productId": 1,
                        "quantity": 1000
                    }
                    """;

            mockMvc.perform(post("/api/v1/cart/items")
                            .with(user("100")).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json))
                    .andExpect(status().isConflict());
        }
    }

    // ==================== PUT /api/v1/cart/items/{productId} ====================

    @Nested
    @DisplayName("PUT /api/v1/cart/items/{productId} — обновление количества")
    class UpdateItemTests {

        @Test
        @DisplayName("200 OK — количество обновлено")
        void shouldUpdateItemQuantity() throws Exception {
            Cart cart = buildCartWithItem(PRODUCT_ID, 20);
            Map<Long, ProductDto> products = Map.of(
                    PRODUCT_ID, buildProduct(PRODUCT_ID, "Доска обрезная", "1500.00"));

            when(cartService.updateItemQuantity(USER_ID, PRODUCT_ID, 20)).thenReturn(cart);
            when(cartService.fetchProductsForCart(cart)).thenReturn(products);

            String json = """
                    {
                        "quantity": 20
                    }
                    """;

            mockMvc.perform(put("/api/v1/cart/items/{productId}", PRODUCT_ID)
                            .with(user("100")).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items[0].quantity").value(20));

            verify(cartService).updateItemQuantity(USER_ID, PRODUCT_ID, 20);
        }

        @Test
        @DisplayName("200 OK — quantity=0 удаляет товар")
        void shouldRemoveItemWhenQuantityZero() throws Exception {
            Cart cart = emptyCart();
            when(cartService.updateItemQuantity(USER_ID, PRODUCT_ID, 0)).thenReturn(cart);

            String json = """
                    {
                        "quantity": 0
                    }
                    """;

            mockMvc.perform(put("/api/v1/cart/items/{productId}", PRODUCT_ID)
                            .with(user("100")).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items").isEmpty());
        }

        @Test
        @DisplayName("400 Bad Request — отрицательный quantity")
        void shouldReturn400WhenQuantityNegative() throws Exception {
            String json = """
                    {
                        "quantity": -5
                    }
                    """;

            mockMvc.perform(put("/api/v1/cart/items/{productId}", PRODUCT_ID)
                            .with(user("100")).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json))
                    .andExpect(status().isBadRequest());
        }
    }

    // ==================== DELETE /api/v1/cart/items/{productId} ====================

    @Nested
    @DisplayName("DELETE /api/v1/cart/items/{productId} — удаление товара")
    class RemoveItemTests {

        @Test
        @DisplayName("200 OK — товар удалён")
        void shouldRemoveItem() throws Exception {
            Cart cart = emptyCart();
            when(cartService.removeItem(USER_ID, PRODUCT_ID)).thenReturn(cart);

            mockMvc.perform(delete("/api/v1/cart/items/{productId}", PRODUCT_ID)
                            .with(user("100")).with(csrf()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items").isEmpty());

            verify(cartService).removeItem(USER_ID, PRODUCT_ID);
        }
    }

    // ==================== DELETE /api/v1/cart ====================

    @Nested
    @DisplayName("DELETE /api/v1/cart — очистка корзины")
    class ClearCartTests {

        @Test
        @DisplayName("204 No Content — корзина очищена")
        void shouldClearCart() throws Exception {
            mockMvc.perform(delete("/api/v1/cart")
                            .with(user("100")).with(csrf()))
                    .andExpect(status().isNoContent());

            verify(cartService).clearCart(USER_ID);
        }
    }

    // ==================== POST /api/v1/cart/persist ====================

    @Nested
    @DisplayName("POST /api/v1/cart/persist — сохранение в БД")
    class PersistCartTests {

        @Test
        @DisplayName("204 No Content — корзина сохранена")
        void shouldPersistCart() throws Exception {
            mockMvc.perform(post("/api/v1/cart/persist")
                            .with(user("100")).with(csrf()))
                    .andExpect(status().isNoContent());

            verify(cartService).saveCartToDB(USER_ID);
        }
    }

    // ==================== Test data builders ====================

    private Cart buildCartWithItem(Long productId, int quantity) {
        Cart cart = Cart.builder().userId(USER_ID).build();
        CartItem item = CartItem.builder()
                .cart(cart).productId(productId).quantity(quantity).build();
        cart.getItems().add(item);
        return cart;
    }

    private Cart emptyCart() {
        return Cart.builder().userId(USER_ID).build();
    }

    private ProductDto buildProduct(Long id, String name, String price) {
        return new ProductDto(id, name, new BigDecimal(price), 100, true);
    }
}
