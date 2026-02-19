package ru.rfsnab.orderservice.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import ru.rfsnab.orderservice.BaseServiceIntegrationTest;
import ru.rfsnab.orderservice.exception.InsufficientStockException;
import ru.rfsnab.orderservice.exception.ProductNotFoundException;
import ru.rfsnab.orderservice.models.dto.product.ProductDto;
import ru.rfsnab.orderservice.models.entity.Cart;
import ru.rfsnab.orderservice.models.entity.CartItem;
import ru.rfsnab.orderservice.repository.CartRedisRepository;
import ru.rfsnab.orderservice.repository.CartRepository;
import ru.rfsnab.orderservice.service.client.ProductServiceClient;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Интеграционные тесты CartService.
 * Реальные Redis и PostgreSQL через Testcontainers.
 * ProductServiceClient замокан — внешний сервис.
 *
 * Тестируем:
 * - Redis ↔ PostgreSQL синхронизацию
 * - afterCommit поведение (Redis очищается только после commit)
 * - Восстановление корзины из БД
 * - Валидацию товаров и остатков
 */
@DisplayName("CartService Integration")
class CartServiceIntegrationTest extends BaseServiceIntegrationTest {

    @Autowired
    private CartService cartService;

    @Autowired
    private CartRedisRepository cartRedisRepository;

    @Autowired
    private CartRepository cartRepository;

//    @MockitoBean
//    private ProductServiceClient productServiceClient;

    private static final Long USER_ID = 100L;
    private static final Long PRODUCT_ID_1 = 1L;
    private static final Long PRODUCT_ID_2 = 2L;

    @BeforeEach
    void cleanUp() {
        cartRedisRepository.clearCart(USER_ID);
        cartRepository.deleteByUserId(USER_ID);
    }

    // ==================== getCart ====================

    @Nested
    @DisplayName("getCart")
    class GetCartTests {

        @Test
        @DisplayName("возвращает корзину из Redis")
        void shouldReturnCartFromRedis() {
            cartRedisRepository.addItem(USER_ID, PRODUCT_ID_1, 10);
            cartRedisRepository.addItem(USER_ID, PRODUCT_ID_2, 5);

            Cart cart = cartService.getCart(USER_ID);

            assertThat(cart.getUserId()).isEqualTo(USER_ID);
            assertThat(cart.getItems()).hasSize(2);
        }

        @Test
        @DisplayName("восстанавливает корзину из PostgreSQL если Redis пуст")
        void shouldRestoreFromDatabaseWhenRedisEmpty() {
            // Сохраняем напрямую в БД
            Cart dbCart = Cart.builder().userId(USER_ID).build();
            CartItem item = CartItem.builder()
                    .cart(dbCart).productId(PRODUCT_ID_1).quantity(7).build();
            dbCart.getItems().add(item);
            cartRepository.save(dbCart);

            Cart cart = cartService.getCart(USER_ID);

            assertThat(cart.getItems()).hasSize(1);
            assertThat(cart.getItems().getFirst().getQuantity()).isEqualTo(7);
            // Данные должны быть скопированы в Redis
            assertThat(cartRedisRepository.exists(USER_ID)).isTrue();
            Map<Long, Integer> redisCart = cartRedisRepository.getCart(USER_ID);
            assertThat(redisCart).containsEntry(PRODUCT_ID_1, 7);
        }

        @Test
        @DisplayName("возвращает пустую корзину если нет данных нигде")
        void shouldReturnEmptyCartWhenNoData() {
            Cart cart = cartService.getCart(USER_ID);

            assertThat(cart.getUserId()).isEqualTo(USER_ID);
            assertThat(cart.getItems()).isEmpty();
        }

        @Test
        @DisplayName("при восстановлении из БД данные остаются в PostgreSQL (backup)")
        void shouldKeepDatabaseBackupAfterRestore() {
            Cart dbCart = Cart.builder().userId(USER_ID).build();
            CartItem item = CartItem.builder()
                    .cart(dbCart).productId(PRODUCT_ID_1).quantity(3).build();
            dbCart.getItems().add(item);
            cartRepository.save(dbCart);

            cartService.getCart(USER_ID);

            // БД не очищена — backup остаётся
            Optional<Cart> stillInDb = cartRepository.findByUserId(USER_ID);
            assertThat(stillInDb).isPresent();
        }
    }

    // ==================== addItemToCart ====================

    @Nested
    @DisplayName("addItemToCart")
    class AddItemTests {

        @Test
        @DisplayName("добавляет новый товар в пустую корзину")
        void shouldAddNewItemToEmptyCart() {
            mockProduct(PRODUCT_ID_1, "Доска", "1500.00", 100);

            Cart cart = cartService.addItemToCart(USER_ID, PRODUCT_ID_1, 5);

            assertThat(cart.getItems()).hasSize(1);
            assertThat(cart.getItems().getFirst().getProductId()).isEqualTo(PRODUCT_ID_1);
            assertThat(cart.getItems().getFirst().getQuantity()).isEqualTo(5);
        }

        @Test
        @DisplayName("увеличивает количество существующего товара")
        void shouldIncrementExistingItemQuantity() {
            mockProduct(PRODUCT_ID_1, "Доска", "1500.00", 100);
            cartRedisRepository.addItem(USER_ID, PRODUCT_ID_1, 10);

            Cart cart = cartService.addItemToCart(USER_ID, PRODUCT_ID_1, 5);

            assertThat(cart.getItems()).hasSize(1);
            assertThat(cart.getItems().getFirst().getQuantity()).isEqualTo(15);
        }

        @Test
        @DisplayName("восстанавливает корзину из БД перед добавлением")
        void shouldRestoreFromDbBeforeAdding() {
            mockProduct(PRODUCT_ID_1, "Доска", "1500.00", 100);
            mockProduct(PRODUCT_ID_2, "Брус", "3200.00", 50);

            // Корзина только в БД
            Cart dbCart = Cart.builder().userId(USER_ID).build();
            CartItem item = CartItem.builder()
                    .cart(dbCart).productId(PRODUCT_ID_1).quantity(3).build();
            dbCart.getItems().add(item);
            cartRepository.save(dbCart);

            // Добавляем второй товар — должна восстановить из БД + добавить
            Cart cart = cartService.addItemToCart(USER_ID, PRODUCT_ID_2, 2);

            assertThat(cart.getItems()).hasSize(2);
        }

        @Test
        @DisplayName("бросает ProductNotFoundException для неактивного товара")
        void shouldThrowWhenProductInactive() {
            when(productServiceClient.getProduct(PRODUCT_ID_1))
                    .thenReturn(new ProductDto(PRODUCT_ID_1, "Доска", new BigDecimal("1500.00"), 100, false));

            assertThatThrownBy(() -> cartService.addItemToCart(USER_ID, PRODUCT_ID_1, 1))
                    .isInstanceOf(ProductNotFoundException.class);
        }

        @Test
        @DisplayName("бросает InsufficientStockException при превышении остатка")
        void shouldThrowWhenInsufficientStock() {
            mockProduct(PRODUCT_ID_1, "Доска", "1500.00", 10);

            assertThatThrownBy(() -> cartService.addItemToCart(USER_ID, PRODUCT_ID_1, 100))
                    .isInstanceOf(InsufficientStockException.class);
        }

        @Test
        @DisplayName("бросает InsufficientStockException с учётом текущего количества в корзине")
        void shouldThrowWhenCumulativeQuantityExceedsStock() {
            mockProduct(PRODUCT_ID_1, "Доска", "1500.00", 10);
            cartRedisRepository.addItem(USER_ID, PRODUCT_ID_1, 8);

            // 8 уже в корзине + 5 = 13 > 10 на складе
            assertThatThrownBy(() -> cartService.addItemToCart(USER_ID, PRODUCT_ID_1, 5))
                    .isInstanceOf(InsufficientStockException.class);
        }
    }

    // ==================== updateItemQuantity ====================

    @Nested
    @DisplayName("updateItemQuantity")
    class UpdateItemTests {

        @Test
        @DisplayName("обновляет количество товара")
        void shouldUpdateQuantity() {
            mockProduct(PRODUCT_ID_1, "Доска", "1500.00", 100);
            cartRedisRepository.addItem(USER_ID, PRODUCT_ID_1, 10);

            Cart cart = cartService.updateItemQuantity(USER_ID, PRODUCT_ID_1, 20);

            assertThat(cart.getItems()).hasSize(1);
            assertThat(cart.getItems().getFirst().getQuantity()).isEqualTo(20);
        }

        @Test
        @DisplayName("quantity=0 удаляет товар из корзины")
        void shouldRemoveItemWhenQuantityZero() {
            cartRedisRepository.addItem(USER_ID, PRODUCT_ID_1, 10);

            Cart cart = cartService.updateItemQuantity(USER_ID, PRODUCT_ID_1, 0);

            assertThat(cart.getItems()).isEmpty();
            assertThat(cartRedisRepository.getCart(USER_ID)).isEmpty();
        }

        @Test
        @DisplayName("бросает InsufficientStockException при превышении остатка")
        void shouldThrowWhenInsufficientStock() {
            mockProduct(PRODUCT_ID_1, "Доска", "1500.00", 10);
            cartRedisRepository.addItem(USER_ID, PRODUCT_ID_1, 5);

            assertThatThrownBy(() -> cartService.updateItemQuantity(USER_ID, PRODUCT_ID_1, 100))
                    .isInstanceOf(InsufficientStockException.class);
        }
    }

    // ==================== removeItem ====================

    @Nested
    @DisplayName("removeItem")
    class RemoveItemTests {

        @Test
        @DisplayName("удаляет товар, оставляя другие")
        void shouldRemoveSpecificItem() {
            cartRedisRepository.addItem(USER_ID, PRODUCT_ID_1, 10);
            cartRedisRepository.addItem(USER_ID, PRODUCT_ID_2, 5);

            Cart cart = cartService.removeItem(USER_ID, PRODUCT_ID_1);

            assertThat(cart.getItems()).hasSize(1);
            assertThat(cart.getItems().getFirst().getProductId()).isEqualTo(PRODUCT_ID_2);
        }
    }

    // ==================== clearCart ====================

    @Nested
    @DisplayName("clearCart")
    class ClearCartTests {

        @Test
        @DisplayName("очищает Redis и PostgreSQL")
        void shouldClearBothRedisAndDatabase() {
            // Данные в Redis
            cartRedisRepository.addItem(USER_ID, PRODUCT_ID_1, 10);
            // Данные в PostgreSQL
            Cart dbCart = Cart.builder().userId(USER_ID).build();
            CartItem item = CartItem.builder()
                    .cart(dbCart).productId(PRODUCT_ID_1).quantity(10).build();
            dbCart.getItems().add(item);
            cartRepository.save(dbCart);

            cartService.clearCart(USER_ID);

            assertThat(cartRedisRepository.getCart(USER_ID)).isEmpty();
            assertThat(cartRepository.findByUserId(USER_ID)).isEmpty();
        }
    }

    // ==================== saveCartToDB ====================

    @Nested
    @DisplayName("saveCartToDB")
    class SaveToDbTests {

        @Test
        @DisplayName("сохраняет корзину из Redis в PostgreSQL и очищает Redis")
        void shouldPersistCartAndClearRedis() {
            cartRedisRepository.addItem(USER_ID, PRODUCT_ID_1, 10);
            cartRedisRepository.addItem(USER_ID, PRODUCT_ID_2, 5);

            cartService.saveCartToDB(USER_ID);

            // Redis очищен (afterCommit)
            assertThat(cartRedisRepository.getCart(USER_ID)).isEmpty();
            // PostgreSQL содержит данные
            Optional<Cart> dbCart = cartRepository.findByUserId(USER_ID);
            assertThat(dbCart).isPresent();
            assertThat(dbCart.get().getItems()).hasSize(2);
        }

        @Test
        @DisplayName("merge — обновляет существующую корзину в БД")
        void shouldMergeExistingCartInDatabase() {
            // Старая корзина в БД
            Cart dbCart = Cart.builder().userId(USER_ID).build();
            CartItem oldItem = CartItem.builder()
                    .cart(dbCart).productId(PRODUCT_ID_1).quantity(3).build();
            dbCart.getItems().add(oldItem);
            cartRepository.save(dbCart);

            // Новая корзина в Redis
            cartRedisRepository.addItem(USER_ID, PRODUCT_ID_2, 7);

            cartService.saveCartToDB(USER_ID);

            Optional<Cart> updated = cartRepository.findByUserId(USER_ID);
            assertThat(updated).isPresent();
            // Должен быть только новый item (старый удалён через orphanRemoval)
            assertThat(updated.get().getItems()).hasSize(1);
            assertThat(updated.get().getItems().getFirst().getProductId()).isEqualTo(PRODUCT_ID_2);
            assertThat(updated.get().getItems().getFirst().getQuantity()).isEqualTo(7);
        }

        @Test
        @DisplayName("не сохраняет пустую корзину")
        void shouldNotPersistEmptyCart() {
            // Redis пуст
            cartService.saveCartToDB(USER_ID);

            assertThat(cartRepository.findByUserId(USER_ID)).isEmpty();
        }
    }

    // ==================== fetchProductsForCart ====================

    @Nested
    @DisplayName("fetchProductsForCart")
    class FetchProductsTests {

        @Test
        @DisplayName("возвращает данные о товарах для позиций корзины")
        void shouldFetchProductsForCartItems() {
            Cart cart = Cart.builder().userId(USER_ID).build();
            CartItem item1 = CartItem.builder()
                    .cart(cart).productId(PRODUCT_ID_1).quantity(10).build();
            CartItem item2 = CartItem.builder()
                    .cart(cart).productId(PRODUCT_ID_2).quantity(5).build();
            cart.getItems().add(item1);
            cart.getItems().add(item2);

            ProductDto product1 = buildProduct(PRODUCT_ID_1, "Доска", "1500.00");
            ProductDto product2 = buildProduct(PRODUCT_ID_2, "Брус", "3200.00");
            when(productServiceClient.getProducts(Set.of(PRODUCT_ID_1, PRODUCT_ID_2)))
                    .thenReturn(Map.of(PRODUCT_ID_1, product1, PRODUCT_ID_2, product2));

            Map<Long, ProductDto> products = cartService.fetchProductsForCart(cart);

            assertThat(products).hasSize(2);
            assertThat(products).containsKeys(PRODUCT_ID_1, PRODUCT_ID_2);
        }
    }

    // ==================== Полный цикл ====================

    @Nested
    @DisplayName("Full lifecycle")
    class FullLifecycleTests {

        @Test
        @DisplayName("полный цикл: добавить → сохранить в БД → восстановить → очистить")
        void shouldHandleFullCartLifecycle() {
            mockProduct(PRODUCT_ID_1, "Доска", "1500.00", 100);

            // 1. Добавляем товар
            cartService.addItemToCart(USER_ID, PRODUCT_ID_1, 10);
            assertThat(cartRedisRepository.exists(USER_ID)).isTrue();

            // 2. Сохраняем в БД (имитация logout)
            cartService.saveCartToDB(USER_ID);
            assertThat(cartRedisRepository.getCart(USER_ID)).isEmpty();
            assertThat(cartRepository.findByUserId(USER_ID)).isPresent();

            // 3. Восстанавливаем (имитация нового входа)
            Cart restored = cartService.getCart(USER_ID);
            assertThat(restored.getItems()).hasSize(1);
            assertThat(restored.getItems().getFirst().getQuantity()).isEqualTo(10);
            assertThat(cartRedisRepository.exists(USER_ID)).isTrue();

            // 4. Очищаем (имитация оформления заказа)
            cartService.clearCart(USER_ID);
            assertThat(cartRedisRepository.getCart(USER_ID)).isEmpty();
            assertThat(cartRepository.findByUserId(USER_ID)).isEmpty();
        }
    }

    // ==================== Helpers ====================

    private void mockProduct(Long id, String name, String price, int stock) {
        ProductDto product = new ProductDto(id, name, new BigDecimal(price), stock, true);
        when(productServiceClient.getProduct(id)).thenReturn(product);
        when(productServiceClient.getProducts(Set.of(id)))
                .thenReturn(Map.of(id, product));
    }

    private ProductDto buildProduct(Long id, String name, String price) {
        return new ProductDto(id, name, new BigDecimal(price), 100, true);
    }
}
