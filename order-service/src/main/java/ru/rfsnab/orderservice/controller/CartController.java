package ru.rfsnab.orderservice.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import ru.rfsnab.orderservice.mapper.CartMapper;
import ru.rfsnab.orderservice.models.dto.cart.AddToCartRequest;
import ru.rfsnab.orderservice.models.dto.cart.CartDto;
import ru.rfsnab.orderservice.models.dto.cart.UpdateCartItemRequest;
import ru.rfsnab.orderservice.models.entity.Cart;
import ru.rfsnab.orderservice.service.CartService;

/**
 * REST контроллер для управления корзиной.
 * userId извлекается из JWT токена (subject).
 */
@RestController
@RequestMapping("/api/v1/cart")
@RequiredArgsConstructor
@Tag(name = "Cart", description = "Управление корзиной покупок")
@SecurityRequirement(name = "Bearer Authentication")
public class CartController {

    private final CartService cartService;
    private final CartMapper cartMapper;

    @GetMapping
    @Operation(summary = "Получить корзину")
    public ResponseEntity<CartDto> getCart(Authentication authentication) {
        Long userId = getCurrentUserId(authentication);
        Cart cart = cartService.getCart(userId);
        return ResponseEntity.ok(cartMapper.toDto(cart));
    }

    @PostMapping("/items")
    @Operation(summary = "Добавить товар в корзину")
    public ResponseEntity<CartDto> addItem(
            Authentication authentication,
            @Valid @RequestBody AddToCartRequest request) {
        Long userId = getCurrentUserId(authentication);
        Cart cart = cartService.addItemToCart(userId, request.productId(), request.quantity());
        return ResponseEntity.ok(cartMapper.toDto(cart));
    }

    @PutMapping("/items/{productId}")
    @Operation(summary = "Изменить количество товара")
    public ResponseEntity<CartDto> updateItemQuantity(
            Authentication authentication,
            @PathVariable Long productId,
            @Valid @RequestBody UpdateCartItemRequest request) {
        Long userId = getCurrentUserId(authentication);
        Cart cart = cartService.updateItemQuantity(userId, productId, request.quantity());
        return ResponseEntity.ok(cartMapper.toDto(cart));
    }

    @DeleteMapping("/items/{productId}")
    @Operation(summary = "Удалить товар из корзины")
    public ResponseEntity<CartDto> removeItem(
            Authentication authentication,
            @PathVariable Long productId) {
        Long userId = getCurrentUserId(authentication);
        Cart cart = cartService.removeItem(userId, productId);
        return ResponseEntity.ok(cartMapper.toDto(cart));
    }

    @DeleteMapping
    @Operation(summary = "Очистить корзину")
    public ResponseEntity<Void> clearCart(Authentication authentication) {
        Long userId = getCurrentUserId(authentication);
        cartService.clearCart(userId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/persist")
    @Operation(summary = "Сохранить корзину в БД")
    public ResponseEntity<Void> persistCart(Authentication authentication) {
        Long userId = getCurrentUserId(authentication);
        cartService.saveCartToDB(userId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Извлечение userId из JWT токена.
     */
    private Long getCurrentUserId(Authentication authentication) {
        return Long.parseLong(authentication.getName());
    }
}