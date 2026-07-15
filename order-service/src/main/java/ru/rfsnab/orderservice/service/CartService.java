package ru.rfsnab.orderservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import ru.rfsnab.orderservice.exception.InsufficientStockException;
import ru.rfsnab.orderservice.exception.ProductNotFoundException;
import ru.rfsnab.orderservice.models.dto.product.ProductDto;
import ru.rfsnab.orderservice.models.entity.Cart;
import ru.rfsnab.orderservice.models.entity.CartItem;
import ru.rfsnab.orderservice.repository.CartRedisRepository;
import ru.rfsnab.orderservice.repository.CartRepository;
import ru.rfsnab.orderservice.service.client.ProductServiceClient;
import ru.rfsnab.orderservice.service.client.UserServiceClient;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Сервис управления корзиной.
 * Стратегия хранения:
 * - Активная сессия: Redis (primary storage, TTL 7 дней)
 * - Завершение сессии / logout: Redis → PostgreSQL (backup)
 * - Новая сессия: PostgreSQL → Redis (восстановление)
 * - Оформление заказа: очистка Redis + PostgreSQL
 * Redis-операции выполняются вне DB-транзакций. Очистка Redis
 * происходит через afterCommit, чтобы не потерять данные при rollback.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CartService {

    private final CartRedisRepository cartRedisRepository;
    private final CartRepository cartRepository;
    private final ProductServiceClient productServiceClient;
    private final UserServiceClient userServiceClient;

    public Cart getCart(Long userId) {
        Map<Long, Integer> items = cartRedisRepository.getCart(userId);
        if (items.isEmpty()) {
            return loadFromDatabaseOrEmpty(userId);
        }
        return buildCartFromRedis(userId, items);
    }

    public Map<Long, ProductDto> fetchProductsForCart(Cart cart) {
        Set<Long> productIds = cart.getItems().stream()
                .map(CartItem::getProductId)
                .collect(Collectors.toSet());
        return productServiceClient.getProducts(productIds);
    }

    public Cart addItemToCart(Long userId, Long productId, int quantity) {
        userServiceClient.requireCompleteProfile(userId);

        ProductDto product = productServiceClient.getProduct(productId);

        if (!product.isActive()) {
            throw new ProductNotFoundException("Product is not available " + productId);
        }

        ensureCartInRedis(userId);

        Map<Long, Integer> currentCart = cartRedisRepository.getCart(userId);
        int currentQuantity = currentCart.getOrDefault(productId, 0);
        int newQuantity = currentQuantity + quantity;

        cartRedisRepository.addItem(userId, productId, newQuantity);
        log.debug("Товар {} добавлен в корзину пользователя {}, количество: {}", productId, userId, newQuantity);
        return getCart(userId);
    }

    public Cart updateItemQuantity(Long userId, Long productId, int quantity) {
        if (quantity > 0) {
            productServiceClient.getProduct(productId);
        }
        cartRedisRepository.updateItemQuantity(userId, productId, quantity);
        return getCart(userId);
    }

    public Cart removeItem(Long userId, Long productId) {
        cartRedisRepository.removeItem(userId, productId);
        return getCart(userId);
    }

    @Transactional
    public void clearCart(Long userId) {
        cartRepository.deleteByUserId(userId);
        registerAfterCommit(() -> {
            cartRedisRepository.clearCart(userId);
            log.debug("Корзина пользователя {} очищена из Redis (afterCommit)", userId);
        });
    }

    @Transactional
    public void saveCartToDB(Long userId) {
        Map<Long, Integer> redisItems = cartRedisRepository.getCart(userId);
        if (redisItems.isEmpty()) {
            return;
        }

        Cart cart = cartRepository.findByUserId(userId)
                .orElseGet(() -> Cart.builder().userId(userId).build());

        cart.getItems().clear();

        redisItems.forEach((productId, quantity) -> {
            CartItem item = CartItem.builder()
                    .cart(cart)
                    .productId(productId)
                    .quantity(quantity)
                    .build();
            cart.getItems().add(item);
        });

        cartRepository.save(cart);
        log.info("Корзина пользователя {} сохранена в БД ({} позиций)", userId, redisItems.size());

        registerAfterCommit(() -> {
            cartRedisRepository.clearCart(userId);
            log.debug("Корзина пользователя {} очищена из Redis после persist (afterCommit)", userId);
        });
    }

    private Cart loadFromDatabaseOrEmpty(Long userId) {
        return cartRepository.findByUserId(userId)
                .map(cart -> {
                    cart.getItems().forEach(item ->
                            cartRedisRepository.addItem(userId, item.getProductId(), item.getQuantity()));
                    log.debug("Корзина пользователя {} восстановлена из БД в Redis ({} позиций)",
                            userId, cart.getItems().size());
                    return cart;
                })
                .orElseGet(() -> emptyCart(userId));
    }

    private void ensureCartInRedis(Long userId) {
        if (!cartRedisRepository.exists(userId)) {
            loadFromDatabaseOrEmpty(userId);
        }
    }

    private Cart buildCartFromRedis(Long userId, Map<Long, Integer> items) {
        Cart cart = Cart.builder().userId(userId).build();
        items.forEach((productId, quantity) -> {
            CartItem item = CartItem.builder()
                    .cart(cart)
                    .productId(productId)
                    .quantity(quantity)
                    .build();
            cart.getItems().add(item);
        });
        return cart;
    }

    private Cart emptyCart(Long userId) {
        return Cart.builder().userId(userId).build();
    }

    private void validateStock(ProductDto product, int requestedQuantity) {
        if (product.stockQuantity() < requestedQuantity) {
            throw new InsufficientStockException(
                    String.format("Not enough stock for product %s. Available: %d, Requested: %d",
                            product.name(), product.stockQuantity(), requestedQuantity));
        }
    }

    private void registerAfterCommit(Runnable action) {
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        action.run();
                    }
                }
        );
    }
}
