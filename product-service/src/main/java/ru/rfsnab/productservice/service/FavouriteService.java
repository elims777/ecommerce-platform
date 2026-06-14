package ru.rfsnab.productservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.rfsnab.productservice.exception.ProductNotFoundException;
import ru.rfsnab.productservice.model.Product;
import ru.rfsnab.productservice.model.UserFavourite;
import ru.rfsnab.productservice.repository.ProductRepository;
import ru.rfsnab.productservice.repository.UserFavouriteRepository;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class FavouriteService {

    private final UserFavouriteRepository favouriteRepository;
    private final ProductRepository productRepository;

    public List<Product> getFavouriteProducts(String userEmail) {
        log.debug("Получение избранного для пользователя: {}", userEmail);
        return favouriteRepository.findByUserEmail(userEmail).stream()
                .map(UserFavourite::getProduct)
                .collect(Collectors.toList());
    }

    public Set<Long> getFavouriteIds(String userEmail) {
        return favouriteRepository.findByUserEmail(userEmail).stream()
                .map(f -> f.getProduct().getId())
                .collect(Collectors.toSet());
    }

    @Transactional
    public void addFavourite(String userEmail, Long productId) {
        if (favouriteRepository.existsByUserEmailAndProductId(userEmail, productId)) {
            log.debug("Товар {} уже в избранном у {}", productId, userEmail);
            return;
        }
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException(productId));

        UserFavourite favourite = UserFavourite.builder()
                .userEmail(userEmail)
                .product(product)
                .build();
        favouriteRepository.save(favourite);
        log.info("Товар {} добавлен в избранное пользователя {}", productId, userEmail);
    }

    @Transactional
    public void removeFavourite(String userEmail, Long productId) {
        favouriteRepository.deleteByUserEmailAndProductId(userEmail, productId);
        log.info("Товар {} удалён из избранного пользователя {}", productId, userEmail);
    }

    public boolean isFavourite(String userEmail, Long productId) {
        return favouriteRepository.existsByUserEmailAndProductId(userEmail, productId);
    }
}
