package ru.rfsnab.productservice.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.rfsnab.productservice.exception.ProductNotFoundException;
import ru.rfsnab.productservice.model.Product;
import ru.rfsnab.productservice.model.UserFavourite;
import ru.rfsnab.productservice.repository.ProductRepository;
import ru.rfsnab.productservice.repository.UserFavouriteRepository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("FavouriteService Unit Tests")
class FavouriteServiceTest {

    @Mock
    private UserFavouriteRepository favouriteRepository;

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private FavouriteService favouriteService;

    private static final String USER_EMAIL = "user@test.com";
    private Product product1;
    private Product product2;

    @BeforeEach
    void setUp() {
        product1 = Product.builder().id(1L).name("Огнетушитель ОП-5").build();
        product2 = Product.builder().id(2L).name("Огнетушитель ОУ-2").build();
    }

    @Nested
    @DisplayName("getFavouriteProducts()")
    class GetFavouriteProductsTests {

        @Test
        @DisplayName("возвращает список избранных товаров")
        void getFavouriteProducts_ReturnsProducts() {
            UserFavourite fav1 = UserFavourite.builder().userEmail(USER_EMAIL).product(product1).build();
            UserFavourite fav2 = UserFavourite.builder().userEmail(USER_EMAIL).product(product2).build();

            when(favouriteRepository.findByUserEmail(USER_EMAIL)).thenReturn(List.of(fav1, fav2));

            List<Product> result = favouriteService.getFavouriteProducts(USER_EMAIL);

            assertThat(result).hasSize(2);
            assertThat(result).containsExactly(product1, product2);
        }

        @Test
        @DisplayName("возвращает пустой список если нет избранного")
        void getFavouriteProducts_Empty_ReturnsEmptyList() {
            when(favouriteRepository.findByUserEmail(USER_EMAIL)).thenReturn(List.of());

            List<Product> result = favouriteService.getFavouriteProducts(USER_EMAIL);

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("getFavouriteIds()")
    class GetFavouriteIdsTests {

        @Test
        @DisplayName("возвращает набор ID избранных товаров")
        void getFavouriteIds_ReturnsIds() {
            UserFavourite fav1 = UserFavourite.builder().userEmail(USER_EMAIL).product(product1).build();
            UserFavourite fav2 = UserFavourite.builder().userEmail(USER_EMAIL).product(product2).build();

            when(favouriteRepository.findByUserEmail(USER_EMAIL)).thenReturn(List.of(fav1, fav2));

            Set<Long> result = favouriteService.getFavouriteIds(USER_EMAIL);

            assertThat(result).containsExactlyInAnyOrder(1L, 2L);
        }

        @Test
        @DisplayName("возвращает пустой набор если нет избранного")
        void getFavouriteIds_Empty_ReturnsEmptySet() {
            when(favouriteRepository.findByUserEmail(USER_EMAIL)).thenReturn(List.of());

            Set<Long> result = favouriteService.getFavouriteIds(USER_EMAIL);

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("addFavourite()")
    class AddFavouriteTests {

        @Test
        @DisplayName("добавляет товар в избранное")
        void addFavourite_NotExists_SavesFavourite() {
            when(favouriteRepository.existsByUserEmailAndProductId(USER_EMAIL, 1L)).thenReturn(false);
            when(productRepository.findById(1L)).thenReturn(Optional.of(product1));

            favouriteService.addFavourite(USER_EMAIL, 1L);

            verify(favouriteRepository).save(any(UserFavourite.class));
        }

        @Test
        @DisplayName("не дублирует если уже в избранном")
        void addFavourite_AlreadyExists_DoesNotSave() {
            when(favouriteRepository.existsByUserEmailAndProductId(USER_EMAIL, 1L)).thenReturn(true);

            favouriteService.addFavourite(USER_EMAIL, 1L);

            verify(favouriteRepository, never()).save(any());
            verify(productRepository, never()).findById(any());
        }

        @Test
        @DisplayName("выбрасывает исключение если товар не найден")
        void addFavourite_ProductNotFound_ThrowsException() {
            when(favouriteRepository.existsByUserEmailAndProductId(USER_EMAIL, 99L)).thenReturn(false);
            when(productRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> favouriteService.addFavourite(USER_EMAIL, 99L))
                    .isInstanceOf(ProductNotFoundException.class);

            verify(favouriteRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("removeFavourite()")
    class RemoveFavouriteTests {

        @Test
        @DisplayName("удаляет товар из избранного")
        void removeFavourite_CallsRepository() {
            favouriteService.removeFavourite(USER_EMAIL, 1L);

            verify(favouriteRepository).deleteByUserEmailAndProductId(USER_EMAIL, 1L);
        }
    }

    @Nested
    @DisplayName("isFavourite()")
    class IsFavouriteTests {

        @Test
        @DisplayName("возвращает true если товар в избранном")
        void isFavourite_Exists_ReturnsTrue() {
            when(favouriteRepository.existsByUserEmailAndProductId(USER_EMAIL, 1L)).thenReturn(true);

            assertThat(favouriteService.isFavourite(USER_EMAIL, 1L)).isTrue();
        }

        @Test
        @DisplayName("возвращает false если товара нет в избранном")
        void isFavourite_NotExists_ReturnsFalse() {
            when(favouriteRepository.existsByUserEmailAndProductId(USER_EMAIL, 1L)).thenReturn(false);

            assertThat(favouriteService.isFavourite(USER_EMAIL, 1L)).isFalse();
        }
    }
}
