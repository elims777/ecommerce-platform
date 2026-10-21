package ru.rfsnab.productservice.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.rfsnab.productservice.dto.ProductStockResponse;
import ru.rfsnab.productservice.exception.BusinessException;
import ru.rfsnab.productservice.model.Product;
import ru.rfsnab.productservice.model.ProductStock;
import ru.rfsnab.productservice.model.Warehouse;
import ru.rfsnab.productservice.repository.ProductRepository;
import ru.rfsnab.productservice.repository.ProductStockRepository;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProductStockService Unit Tests")
class ProductStockServiceTest {

    @Mock
    private ProductStockRepository stockRepository;

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private ProductStockService stockService;

    private Warehouse syktyvkar;
    private Warehouse pushkin;
    private Warehouse krasnoyarsk;

    @BeforeEach
    void setUp() {
        krasnoyarsk = Warehouse.builder().id(3L).code("KRASNOYARSK").name("Склад Красноярск").city("Красноярск").sortOrder(3).build();
        syktyvkar = Warehouse.builder().id(1L).code("SYKTYVKAR").name("Основной склад").city("Сыктывкар").sortOrder(1).build();
        pushkin = Warehouse.builder().id(2L).code("PUSHKIN").name("Склад Пушкин").city("Пушкин").sortOrder(2).build();
    }

    private Product product(Long id, Long mergedInto) {
        return Product.builder().id(id).name("Товар " + id).mergedIntoProductId(mergedInto).build();
    }

    private ProductStock stock(Product product, Warehouse warehouse, int quantity) {
        return ProductStock.builder().product(product).warehouse(warehouse).quantity(quantity).build();
    }

    @Test
    @DisplayName("setQuantity без существующей строки создаёт её и пересчитывает сумму товара")
    void setQuantity_NoRow_CreatesRowAndRecalcs() {
        Product product = product(1L, null);
        when(stockRepository.findByProductIdAndWarehouseId(1L, 2L)).thenReturn(Optional.empty());
        when(stockRepository.sumActive(1L)).thenReturn(5);

        stockService.setQuantity(product, pushkin, 5);

        ArgumentCaptor<ProductStock> captor = ArgumentCaptor.forClass(ProductStock.class);
        verify(stockRepository).save(captor.capture());
        assertThat(captor.getValue().getProduct()).isSameAs(product);
        assertThat(captor.getValue().getWarehouse()).isSameAs(pushkin);
        assertThat(captor.getValue().getQuantity()).isEqualTo(5);
        assertThat(product.getStockQuantity()).isEqualTo(5);
        verify(productRepository).save(product);
    }

    @Test
    @DisplayName("setQuantity с существующей строкой обновляет quantity, не создавая новую")
    void setQuantity_ExistingRow_UpdatesQuantity() {
        Product product = product(1L, null);
        ProductStock existing = stock(product, pushkin, 3);
        when(stockRepository.findByProductIdAndWarehouseId(1L, 2L)).thenReturn(Optional.of(existing));
        when(stockRepository.sumActive(1L)).thenReturn(9);

        stockService.setQuantity(product, pushkin, 9);

        verify(stockRepository).save(existing);
        assertThat(existing.getQuantity()).isEqualTo(9);
    }

    @Test
    @DisplayName("setQuantity для склеенного товара пересчитывает и главный")
    void setQuantity_MergedProduct_RecalcsMain() {
        Product oneC = product(2L, 10L);
        Product main = product(10L, null);
        when(stockRepository.findByProductIdAndWarehouseId(2L, 1L)).thenReturn(Optional.empty());
        when(stockRepository.sumActive(2L)).thenReturn(50);
        when(stockRepository.sumActive(10L)).thenReturn(280);
        when(productRepository.findById(10L)).thenReturn(Optional.of(main));

        stockService.setQuantity(oneC, syktyvkar, 50);

        assertThat(oneC.getStockQuantity()).isEqualTo(50);
        assertThat(main.getStockQuantity()).isEqualTo(280);
        verify(productRepository).save(main);
    }

    @Test
    @DisplayName("setQuantity с отрицательным количеством — BusinessException")
    void setQuantity_Negative_Throws() {
        Product product = product(1L, null);

        assertThatThrownBy(() -> stockService.setQuantity(product, pushkin, -1))
                .isInstanceOf(BusinessException.class);
        verify(stockRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("getStocks агрегирует свои и склеенные строки, сортирует по sortOrder, пропускает нулевые")
    void getStocks_MergedRows_SortedAndZeroSkipped() {
        Product main = product(1L, null);
        Product oneC = product(2L, 1L);
        when(stockRepository.findActiveForProducts(org.mockito.ArgumentMatchers.anyCollection())).thenReturn(List.of(
                stock(main, pushkin, 230),
                stock(oneC, syktyvkar, 50),
                stock(main, krasnoyarsk, 0)));

        Map<Long, List<ProductStockResponse>> result = stockService.getStocks(List.of(1L));

        assertThat(result.get(1L)).extracting(ProductStockResponse::code, ProductStockResponse::quantity)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("SYKTYVKAR", 50),
                        org.assertj.core.groups.Tuple.tuple("PUSHKIN", 230));
    }

    @Test
    @DisplayName("getStocks суммирует две строки одного склада (своя + склеенная)")
    void getStocks_SameWarehouse_Summed() {
        Product main = product(1L, null);
        Product oneC = product(2L, 1L);
        when(stockRepository.findActiveForProducts(org.mockito.ArgumentMatchers.anyCollection())).thenReturn(List.of(
                stock(main, syktyvkar, 20),
                stock(oneC, syktyvkar, 50)));

        Map<Long, List<ProductStockResponse>> result = stockService.getStocks(List.of(1L));

        assertThat(result.get(1L)).hasSize(1);
        assertThat(result.get(1L).get(0).quantity()).isEqualTo(70);
    }

    @Test
    @DisplayName("getOwnStocks возвращает только собственные строки, включая нулевые")
    void getOwnStocks_OnlyOwnRowsWithZero() {
        Product main = product(1L, null);
        when(stockRepository.findByProductIdIn(List.of(1L))).thenReturn(List.of(
                stock(main, pushkin, 230),
                stock(main, syktyvkar, 0)));

        Map<Long, List<ProductStockResponse>> result = stockService.getOwnStocks(List.of(1L));

        assertThat(result.get(1L)).extracting(ProductStockResponse::code, ProductStockResponse::quantity)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("SYKTYVKAR", 0),
                        org.assertj.core.groups.Tuple.tuple("PUSHKIN", 230));
    }
}
