package ru.rfsnab.orderservice.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.rfsnab.orderservice.exception.InsufficientStockException;
import ru.rfsnab.orderservice.exception.ProductNotFoundException;
import ru.rfsnab.orderservice.models.dto.product.ProductDto;
import ru.rfsnab.orderservice.models.entity.Cart;
import ru.rfsnab.orderservice.models.entity.CartItem;
import ru.rfsnab.orderservice.repository.CartRedisRepository;
import ru.rfsnab.orderservice.repository.CartRepository;
import ru.rfsnab.orderservice.service.client.ProductServiceClient;

import java.util.Map;

/**
 * Сервис управления корзиной.
 *
 * Корзина хранится в Redis для быстрого доступа.
 * При завершении сессии сохраняется в PostgreSQL.
 * При возвращении пользователя — загружается из PostgreSQL в Redis.
 */
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

        Map<Long, Integer> currentCart = cartRedisRepository.getCart(userId);
        int currentQuantity = currentCart.getOrDefault(productId, 0);
        int newQuantity = currentQuantity + quantity;

        validateStock(product, newQuantity);

        cartRedisRepository.addItem(userId,productId,newQuantity);

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
        cartRedisRepository.clearCart(userId);
        cartRepository.findByUserId(userId).ifPresent(cartRepository::delete);
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
        Map<Long, Integer> items = cartRedisRepository.getCart(userId);
        if(items.isEmpty()){
            return;
        }
        // Удаляем старую корзину если есть
        cartRepository.findByUserId(userId).ifPresent(cartRepository::delete);
        // Сохраняем новую
        Cart cart = buildCartFromRedis(userId, items);
        cartRepository.save(cart);

        // Очищаем Redis
        cartRedisRepository.clearCart(userId);
    }

    /**
     * Загрузка корзины из PostgreSQL или создание пустой.
     * При загрузке переносит данные в Redis и удаляет из БД.
     */
    private Cart loadFromDatabaseOrEmpty(Long userId){
        return cartRepository.findByUserId(userId)
                .map(cart -> {
                    // Переносим в Redis
                    cart.getItems().forEach(item ->
                            cartRedisRepository.addItem(userId, item.getProductId(), item.getQuantity()));
                    cartRepository.delete(cart);
                    return cart;
                })
                .orElseGet(()-> emptyCart(userId));
    }

    /**
     * Создание Cart entity из данных Redis.
     * Cart будет transient (не сохранён в БД).
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
}
