package ru.rfsnab.orderservice.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Repository
@RequiredArgsConstructor
public class CartRedisRepository {
    private final RedisTemplate<String, Object> redisTemplate;

    private static final String CART_KEY_PREFIX = "cart:";
    private static final Duration CART_TTL = Duration.ofDays(7);

    private String getKey(Long userId){
        return CART_KEY_PREFIX + userId;
    }

    public void addItem(Long userId, Long productId, int quantity) {
        String key = getKey(userId);
        redisTemplate.opsForHash().put(key, productId.toString(), quantity);
        redisTemplate.expire(key, CART_TTL);
    }

    public void updateItemQuantity(Long userId, Long productId, int quantity) {
        String key = getKey(userId);
        if (quantity <= 0) {
            redisTemplate.opsForHash().delete(key, productId.toString());
        } else {
            redisTemplate.opsForHash().put(key, productId.toString(), quantity);
            redisTemplate.expire(key, CART_TTL);
        }
    }

    public void removeItem(Long userId, Long productId) {
        redisTemplate.opsForHash().delete(getKey(userId), productId.toString());
    }

    public Map<Long, Integer> getCart(Long userId) {
        Map<Object, Object> entries = redisTemplate.opsForHash().entries(getKey(userId));
        Map<Long, Integer> cart = new HashMap<>();
        entries.forEach((k, v) -> cart.put(Long.valueOf(k.toString()), (Integer) v));
        return cart;
    }

    public void clearCart(Long userId) {
        redisTemplate.delete(getKey(userId));
    }

    public boolean exists(Long userId) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(getKey(userId)));
    }

    public long getTtl(Long userId) {
        Long ttl = redisTemplate.getExpire(getKey(userId));
        return ttl != null ? ttl : -1;
    }
}
