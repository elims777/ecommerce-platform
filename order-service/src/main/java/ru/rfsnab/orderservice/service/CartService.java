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

    /**
     * Получение корзины пользователя.
     * Сначала проверяет Redis, затем PostgreSQL.
     *
     * @param userId идентификатор пользователя
     * @return корзина (может быть пустой)
     */
    public Cart getCart(Long userId){
        Map<Long, Integer> items = cartRedisRepository.getCart(userId);

        if(items.isEmpty()){
            return loadFromDatabaseOrEmpty(userId);
        }

        return buildCartFromRedis(userId, items);
    }

    /**
     * Получение данных о товарах из product-service для позиций корзины.
     * Используется контроллером для enrichment при маппинге в DTO.
     *
     * @param cart корзина
     * @return map productId → ProductDto
     */
    public Map<Long, ProductDto> fetchProductsForCart(Cart cart){
        Set<Long> productIds = cart.getItems().stream()
                .map(CartItem::getProductId)
                .collect(Collectors.toSet());
        return productServiceClient.getProducts(productIds);
    }

    /**
     * Добавление товара в корзину.
     * Проверяет существование товара и наличие на складе.
     *
     * @param userId идентификатор пользователя
     * @param productId идентификатор товара
     * @param quantity количество
     * @return обновлённая корзина
     * @throws ProductNotFoundException если товар не найден или неактивен
     * @throws InsufficientStockException если недостаточно товара на складе
     */
    public Cart addItemToCart(Long userId, Long productId, int quantity){
        ProductDto product = productServiceClient.getProduct(productId);

        if(!product.active()){
            throw new ProductNotFoundException("Product is not available " + productId);
        }

        // Гарантируем, что корзина загружена в Redis (если была только в БД)
        ensureCartInRedis(userId);

        Map<Long, Integer> currentCart = cartRedisRepository.getCart(userId);
        int currentQuantity = currentCart.getOrDefault(productId, 0);
        int newQuantity = currentQuantity + quantity;

        validateStock(product, newQuantity);

        cartRedisRepository.addItem(userId,productId,newQuantity);
        log.debug("Товар {} добавлен в корзину пользователя {}, количество: {}",
                productId, userId, newQuantity);
        return getCart(userId);
    }

    /**
     * Обновление количества товара в корзине.
     * Если quantity = 0, товар удаляется из корзины.
     *
     * @param userId идентификатор пользователя
     * @param productId идентификатор товара
     * @param quantity новое количество
     * @return обновлённая корзина
     * @throws InsufficientStockException если недостаточно товара на складе
     */
    public Cart updateItemQuantity(Long userId, Long productId, int quantity){
        if(quantity>0){
            ProductDto product = productServiceClient.getProduct(productId);
            validateStock(product, quantity);
        }
        cartRedisRepository.updateItemQuantity(userId,productId,quantity);
        return getCart(userId);
    }

    /**
     * Удаление товара из корзины.
     *
     * @param userId идентификатор пользователя
     * @param productId идентификатор товара
     * @return обновлённая корзина
     */
    public Cart removeItem(Long userId, Long productId){
        cartRedisRepository.removeItem(userId, productId);
        return getCart(userId);
    }

    /**
     * Полная очистка корзины.
     * Удаляет из Redis и PostgreSQL.
     *
     * @param userId идентификатор пользователя
     */
    @Transactional
    public void clearCart(Long userId){
        cartRepository.deleteByUserId(userId);

        registerAfterCommit(() -> {
            cartRedisRepository.clearCart(userId);
            log.debug("Корзина пользователя {} очищена из Redis (afterCommit)", userId);
        });
    }

    /**
     * Сохранение корзины в PostgreSQL.
     * Вызывается при logout или истечении сессии.
     * Пустая корзина не сохраняется.
     *
     * @param userId идентификатор пользователя
     */
    @Transactional
    public void saveCartToDB(Long userId){
        Map<Long, Integer> redisItems = cartRedisRepository.getCart(userId);
        if (redisItems.isEmpty()) {
            return;
        }

        // Merge: находим существующую или создаём новую
        Cart cart = cartRepository.findByUserId(userId)
                .orElseGet(() -> Cart.builder().userId(userId).build());

        // orphanRemoval удалит старые CartItem при clear
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

        // Очищаем Redis только после успешного commit
        registerAfterCommit(() -> {
            cartRedisRepository.clearCart(userId);
            log.debug("Корзина пользователя {} очищена из Redis после persist (afterCommit)", userId);
        });
    }

    /**
     * Загрузка корзины из PostgreSQL или создание пустой.
     * При загрузке копирует данные в Redis для активной сессии.
     * НЕ удаляет из БД — БД остаётся backup до следующего saveCartToDB или clearCart.
     */
    private Cart loadFromDatabaseOrEmpty(Long userId){
        return cartRepository.findByUserId(userId)
                .map(cart -> {
                    // Копируем в Redis для текущей сессии
                    cart.getItems().forEach(item ->
                            cartRedisRepository.addItem(userId, item.getProductId(), item.getQuantity()));
                    log.debug("Корзина пользователя {} восстановлена из БД в Redis ({} позиций)",
                            userId, cart.getItems().size());
                    return cart;
                })
                .orElseGet(() -> emptyCart(userId));
    }

    /**
     * Гарантирует, что корзина загружена в Redis.
     * Если Redis пуст — проверяет БД и восстанавливает.
     */
    private void ensureCartInRedis(Long userId) {
        if (!cartRedisRepository.exists(userId)) {
            loadFromDatabaseOrEmpty(userId);
        }
    }

    /**
     * Создание Cart entity из данных Redis (transient, без ID).
     */
    private Cart buildCartFromRedis(Long userId, Map<Long, Integer> items) {
        Cart cart = Cart.builder()
                .userId(userId)
                .build();

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

    /**
     * Создание пустой корзины.
     */
    private Cart emptyCart(Long userId) {
        return Cart.builder()
                .userId(userId)
                .build();
    }

    /**
     * Валидация наличия товара на складе.
     */
    private void validateStock(ProductDto product, int requestedQuantity) {
        if (product.stockQuantity() < requestedQuantity) {
            throw new InsufficientStockException(
                    String.format("Not enough stock for product %s. Available: %d, Requested: %d",
                            product.name(), product.stockQuantity(), requestedQuantity));
        }
    }

    /**
     * Регистрация callback после успешного commit транзакции.
     */
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
