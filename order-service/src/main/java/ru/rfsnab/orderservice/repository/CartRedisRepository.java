package ru.rfsnab.orderservice.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

import ru.rfsnab.orderservice.models.entity.enums.CustomerType;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Repository
@RequiredArgsConstructor
public class CartRedisRepository {
    private final RedisTemplate<String, Object> redisTemplate;

    private static final String CART_KEY_PREFIX = "cart:";
    private static final String B2B_CART_KEY_PREFIX = "cart:b2b:";
    private static final Duration CART_TTL = Duration.ofDays(7);

    private String getKey(Long userId, CustomerType customerType){
        // B2C сохраняет прежний формат ключа, чтобы не потерять уже лежащие корзины
        return customerType == CustomerType.B2B ? B2B_CART_KEY_PREFIX + userId : CART_KEY_PREFIX + userId;
    }

    public void addItem(Long userId, CustomerType customerType, Long productId, int quantity) {
        String key = getKey(userId, customerType);
        redisTemplate.opsForHash().put(key, productId.toString(), quantity);
        redisTemplate.expire(key, CART_TTL);
    }

    public void updateItemQuantity(Long userId, CustomerType customerType, Long productId, int quantity) {
        String key = getKey(userId, customerType);
        if (quantity <= 0) {
            redisTemplate.opsForHash().delete(key, productId.toString());
        } else {
            redisTemplate.opsForHash().put(key, productId.toString(), quantity);
            redisTemplate.expire(key, CART_TTL);
        }
    }

    public void removeItem(Long userId, CustomerType customerType, Long productId) {
        redisTemplate.opsForHash().delete(getKey(userId, customerType), productId.toString());
    }

    public Map<Long, Integer> getCart(Long userId, CustomerType customerType) {
        Map<Object, Object> entries = redisTemplate.opsForHash().entries(getKey(userId, customerType));
        Map<Long, Integer> cart = new HashMap<>();
        entries.forEach((k, v) -> cart.put(Long.valueOf(k.toString()), (Integer) v));
        return cart;
    }

    public void clearCart(Long userId, CustomerType customerType) {
        redisTemplate.delete(getKey(userId, customerType));
    }

    public boolean exists(Long userId, CustomerType customerType) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(getKey(userId, customerType)));
    }

    public long getTtl(Long userId, CustomerType customerType) {
        Long ttl = redisTemplate.getExpire(getKey(userId, customerType));
        return ttl != null ? ttl : -1;
    }
}
