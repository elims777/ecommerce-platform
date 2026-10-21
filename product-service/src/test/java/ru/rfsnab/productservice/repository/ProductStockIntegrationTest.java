package ru.rfsnab.productservice.repository;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;
import ru.rfsnab.productservice.BaseIntegrationTest;
import ru.rfsnab.productservice.model.Product;
import ru.rfsnab.productservice.model.ProductStock;
import ru.rfsnab.productservice.model.Warehouse;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Остатки по складам Integration")
class ProductStockIntegrationTest extends BaseIntegrationTest {

    private static final String SYKTYVKAR = "SYKTYVKAR";
    private static final String PUSHKIN = "PUSHKIN";

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductStockRepository stockRepository;

    @Autowired
    private WarehouseRepository warehouseRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private Warehouse syktyvkar;
    private Warehouse pushkin;
    private Product main;
    private Product oneC;

    @BeforeEach
    void setUp() {
        productRepository.deleteAll();
        syktyvkar = warehouseRepository.findByCode(SYKTYVKAR).orElseThrow();
        pushkin = warehouseRepository.findByCode(PUSHKIN).orElseThrow();
        // Миграция создаёт склады; тест может их выключать — возвращаем исходное состояние
        syktyvkar.setActive(true);
        pushkin.setActive(true);
        warehouseRepository.saveAll(java.util.List.of(syktyvkar, pushkin));

        main = saveProduct("Перчатки Факел", "FTK", null);
        oneC = saveProduct("Перчатки 1С", "INTERNAL", main.getId());
        saveStock(main, pushkin, 230);
        saveStock(oneC, syktyvkar, 50);
    }

    // Склады из миграции общие для всех тестов — возвращаем исходное состояние
    @AfterEach
    void restoreWarehouses() {
        pushkin.setActive(true);
        warehouseRepository.save(pushkin);
    }

    private Product saveProduct(String name, String source, Long mergedInto) {
        return productRepository.save(Product.builder()
                .name(name)
                .slug(name.toLowerCase().replace(" ", "-") + "-" + System.nanoTime())
                .source(source)
                .mergedIntoProductId(mergedInto)
                .stockQuantity(0)
                .build());
    }

    private void saveStock(Product product, Warehouse warehouse, int quantity) {
        stockRepository.save(ProductStock.builder().product(product).warehouse(warehouse).quantity(quantity).build());
    }

    @Test
    @DisplayName("миграция создала склады Сыктывкар (самовывоз, sort 1) и Пушкино (sort 2)")
    void migrationSeedsWarehouses() {
        assertThat(syktyvkar.getPickupAvailable()).isTrue();
        assertThat(syktyvkar.getSortOrder()).isEqualTo(1);
        assertThat(pushkin.getPickupAvailable()).isFalse();
        assertThat(pushkin.getSortOrder()).isEqualTo(2);
    }

    @Test
    @DisplayName("sumActive суммирует свои и склеенные строки и игнорирует выключенный склад")
    void sumActive_SkipsInactiveWarehouse() {
        assertThat(stockRepository.sumActive(main.getId())).isEqualTo(280);

        pushkin.setActive(false);
        warehouseRepository.save(pushkin);

        assertThat(stockRepository.sumActive(main.getId())).isEqualTo(50);
    }

    @Test
    @DisplayName("recalcAllTotals выставляет stock_quantity главному и склеенному товару")
    void recalcAllTotals_UpdatesStockQuantity() {
        transactionTemplate.executeWithoutResult(status -> stockRepository.recalcAllTotals());

        assertThat(productRepository.findById(main.getId()).orElseThrow().getStockQuantity()).isEqualTo(280);
        assertThat(productRepository.findById(oneC.getId()).orElseThrow().getStockQuantity()).isEqualTo(50);
    }

    @Test
    @DisplayName("findActiveForProducts возвращает свои и склеенные строки одним запросом")
    void findActiveForProducts_ReturnsOwnAndMerged() {
        var rows = transactionTemplate.execute(status -> stockRepository.findActiveForProducts(java.util.List.of(main.getId()))
                .stream().map(s -> s.getProduct().getId() + ":" + s.getQuantity()).toList());

        assertThat(rows).containsExactlyInAnyOrder(main.getId() + ":230", oneC.getId() + ":50");
    }
}
