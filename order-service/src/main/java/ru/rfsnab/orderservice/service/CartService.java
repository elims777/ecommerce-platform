package ru.rfsnab.orderservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import ru.rfsnab.orderservice.exception.ProductNotFoundException;
import ru.rfsnab.orderservice.models.dto.product.ProductDto;
import ru.rfsnab.orderservice.models.entity.Cart;
import ru.rfsnab.orderservice.models.entity.CartItem;
import ru.rfsnab.orderservice.models.entity.enums.CustomerType;
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

    public Cart getCart(Long userId, CustomerType customerType) {
        Map<Long, Integer> items = cartRedisRepository.getCart(userId, customerType);
        if (items.isEmpty()) {
            return loadFromDatabaseOrEmpty(userId, customerType);
        }
        return buildCartFromRedis(userId, customerType, items);
    }

    public Map<Long, ProductDto> fetchProductsForCart(Cart cart) {
        Set<Long> productIds = cart.getItems().stream()
                .map(CartItem::getProductId)
                .collect(Collectors.toSet());
        return productServiceClient.getProducts(productIds);
    }

    public Cart addItemToCart(Long userId, CustomerType customerType, Long productId, int quantity) {
        // Для B2B userId — id юрлица, профиля пользователя нет
        if (customerType != CustomerType.B2B) {
            userServiceClient.requireCompleteProfile(userId);
        }

        ProductDto product = productServiceClient.getProduct(productId);

        if (!Boolean.TRUE.equals(product.isActive())) {
            throw new ProductNotFoundException("Product is not available " + productId);
        }

        ensureCartInRedis(userId, customerType);

        Map<Long, Integer> currentCart = cartRedisRepository.getCart(userId, customerType);
        int currentQuantity = currentCart.getOrDefault(productId, 0);
        int newQuantity = currentQuantity + quantity;

        cartRedisRepository.addItem(userId, customerType, productId, newQuantity);
        log.debug("Товар {} добавлен в корзину пользователя {}, количество: {}", productId, userId, newQuantity);
        return getCart(userId, customerType);
    }

    public Cart updateItemQuantity(Long userId, CustomerType customerType, Long productId, int quantity) {
        if (quantity > 0) {
            productServiceClient.getProduct(productId);
        }
        cartRedisRepository.updateItemQuantity(userId, customerType, productId, quantity);
        return getCart(userId, customerType);
    }

    public Cart removeItem(Long userId, CustomerType customerType, Long productId) {
        cartRedisRepository.removeItem(userId, customerType, productId);
        return getCart(userId, customerType);
    }

    @Transactional
    public void clearCart(Long userId, CustomerType customerType) {
        cartRepository.deleteByUserIdAndCustomerType(userId, customerType);
        registerAfterCommit(() -> {
            cartRedisRepository.clearCart(userId, customerType);
            log.debug("Корзина пользователя {} очищена из Redis (afterCommit)", userId);
        });
    }

    @Transactional
    public void saveCartToDB(Long userId, CustomerType customerType) {
        Map<Long, Integer> redisItems = cartRedisRepository.getCart(userId, customerType);
        if (redisItems.isEmpty()) {
            return;
        }

        Cart cart = cartRepository.findByUserIdAndCustomerType(userId, customerType)
                .orElseGet(() -> Cart.builder().userId(userId).customerType(customerType).build());

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
            cartRedisRepository.clearCart(userId, customerType);
            log.debug("Корзина пользователя {} очищена из Redis после persist (afterCommit)", userId);
        });
    }

    private Cart loadFromDatabaseOrEmpty(Long userId, CustomerType customerType) {
        return cartRepository.findByUserIdAndCustomerType(userId, customerType)
                .map(cart -> {
                    cart.getItems().forEach(item ->
                            cartRedisRepository.addItem(userId, customerType, item.getProductId(), item.getQuantity()));
                    log.debug("Корзина пользователя {} восстановлена из БД в Redis ({} позиций)",
                            userId, cart.getItems().size());
                    return cart;
                })
                .orElseGet(() -> emptyCart(userId, customerType));
    }

    private void ensureCartInRedis(Long userId, CustomerType customerType) {
        if (!cartRedisRepository.exists(userId, customerType)) {
            loadFromDatabaseOrEmpty(userId, customerType);
        }
    }

    private Cart buildCartFromRedis(Long userId, CustomerType customerType, Map<Long, Integer> items) {
        Cart cart = Cart.builder().userId(userId).customerType(customerType).build();
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

    private Cart emptyCart(Long userId, CustomerType customerType) {
        return Cart.builder().userId(userId).customerType(customerType).build();
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
