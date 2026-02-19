package ru.rfsnab.orderservice.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
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
import ru.rfsnab.orderservice.models.dto.product.ProductDto;
import ru.rfsnab.orderservice.models.entity.Cart;
import ru.rfsnab.orderservice.service.CartService;

import java.util.Map;

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

    @GetMapping
    @Operation(summary = "Получить корзину",
            description = "Возвращает текущую корзину пользователя с данными о товарах и ценами")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Корзина получена"),
            @ApiResponse(responseCode = "401", description = "Не авторизован")
    })
    public ResponseEntity<CartDto> getCart(Authentication authentication) {
        Long userId = getCurrentUserId(authentication);
        return ResponseEntity.ok(toCartDto(userId));
    }

    @PostMapping("/items")
    @Operation(summary = "Добавить товар в корзину",
            description = "Добавляет товар или увеличивает количество, если уже есть в корзине")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Товар добавлен"),
            @ApiResponse(responseCode = "400", description = "Невалидный запрос"),
            @ApiResponse(responseCode = "404", description = "Товар не найден"),
            @ApiResponse(responseCode = "409", description = "Недостаточно товара на складе")
    })
    public ResponseEntity<CartDto> addItem(
            Authentication authentication,
            @Valid @RequestBody AddToCartRequest request) {
        Long userId = getCurrentUserId(authentication);
        Cart cart = cartService.addItemToCart(userId, request.productId(), request.quantity());
        Map<Long, ProductDto> products = cartService.fetchProductsForCart(cart);
        return ResponseEntity.ok(CartMapper.toDto(cart, products));
    }

    @PutMapping("/items/{productId}")
    @Operation(summary = "Изменить количество товара",
            description = "Устанавливает новое количество. При quantity=0 товар удаляется")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Количество обновлено"),
            @ApiResponse(responseCode = "400", description = "Невалидный запрос"),
            @ApiResponse(responseCode = "409", description = "Недостаточно товара на складе")
    })
    public ResponseEntity<CartDto> updateItemQuantity(
            Authentication authentication,
            @PathVariable Long productId,
            @Valid @RequestBody UpdateCartItemRequest request) {
        Long userId = getCurrentUserId(authentication);
        Cart cart = cartService.updateItemQuantity(userId, productId, request.quantity());
        Map<Long, ProductDto> products = cartService.fetchProductsForCart(cart);
        return ResponseEntity.ok(CartMapper.toDto(cart, products));
    }

    @DeleteMapping("/items/{productId}")
    @Operation(summary = "Удалить товар из корзины")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Товар удалён"),
            @ApiResponse(responseCode = "401", description = "Не авторизован")
    })
    public ResponseEntity<CartDto> removeItem(
            Authentication authentication,
            @PathVariable Long productId) {
        Long userId = getCurrentUserId(authentication);
        Cart cart = cartService.removeItem(userId, productId);
        Map<Long, ProductDto> products = cartService.fetchProductsForCart(cart);
        return ResponseEntity.ok(CartMapper.toDto(cart, products));
    }

    @DeleteMapping
    @Operation(summary = "Очистить корзину",
            description = "Полная очистка корзины из Redis и PostgreSQL")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Корзина очищена"),
            @ApiResponse(responseCode = "401", description = "Не авторизован")
    })
    public ResponseEntity<Void> clearCart(Authentication authentication) {
        Long userId = getCurrentUserId(authentication);
        cartService.clearCart(userId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/persist")
    @Operation(summary = "Сохранить корзину в БД",
            description = "Переносит корзину из Redis в PostgreSQL. Вызывается при logout или завершении сессии")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Корзина сохранена"),
            @ApiResponse(responseCode = "401", description = "Не авторизован")
    })
    public ResponseEntity<Void> persistCart(Authentication authentication) {
        Long userId = getCurrentUserId(authentication);
        cartService.saveCartToDB(userId);
        return ResponseEntity.noContent().build();
    }

    // ==================== Private methods ====================

    /**
     * Получение корзины и маппинг в DTO с enrichment.
     * Вынесено для переиспользования в GET /cart.
     */
    private CartDto toCartDto(Long userId) {
        Cart cart = cartService.getCart(userId);
        if (cart.getItems().isEmpty()) {
            return CartMapper.emptyCartDto(userId);
        }
        Map<Long, ProductDto> products = cartService.fetchProductsForCart(cart);
        return CartMapper.toDto(cart, products);
    }

    /**
     * Извлечение userId из JWT токена.
     */
    private Long getCurrentUserId(Authentication authentication) {
        return Long.parseLong(authentication.getName());
    }
}