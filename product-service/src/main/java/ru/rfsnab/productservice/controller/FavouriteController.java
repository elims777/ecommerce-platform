package ru.rfsnab.productservice.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import ru.rfsnab.productservice.dto.ProductResponse;
import ru.rfsnab.productservice.mapper.ProductMapper;
import ru.rfsnab.productservice.service.FavouriteService;

import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/v1/favourites")
@RequiredArgsConstructor
@Tag(name = "Favourites", description = "Управление избранными товарами")
@SecurityRequirement(name = "Bearer Authentication")
public class FavouriteController {

    private final FavouriteService favouriteService;

    @GetMapping("/ids")
    @Operation(summary = "Получить ID избранных товаров")
    public ResponseEntity<Set<Long>> getFavouriteIds(Authentication authentication) {
        return ResponseEntity.ok(favouriteService.getFavouriteIds(authentication.getName()));
    }

    @GetMapping("/products")
    @Operation(summary = "Получить избранные товары с полными данными")
    public ResponseEntity<List<ProductResponse>> getFavouriteProducts(Authentication authentication) {
        return ResponseEntity.ok(
                favouriteService.getFavouriteProducts(authentication.getName()).stream()
                        .map(ProductMapper::mapToResponse)
                        .toList()
        );
    }

    @PostMapping("/{productId}")
    @Operation(summary = "Добавить товар в избранное")
    public ResponseEntity<Void> addFavourite(
            @PathVariable Long productId,
            Authentication authentication) {
        favouriteService.addFavourite(authentication.getName(), productId);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{productId}")
    @Operation(summary = "Удалить товар из избранного")
    public ResponseEntity<Void> removeFavourite(
            @PathVariable Long productId,
            Authentication authentication) {
        favouriteService.removeFavourite(authentication.getName(), productId);
        return ResponseEntity.noContent().build();
    }
}
