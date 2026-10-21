package ru.rfsnab.productservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.rfsnab.productservice.dto.ProductStockResponse;
import ru.rfsnab.productservice.exception.BusinessException;
import ru.rfsnab.productservice.mapper.WarehouseMapper;
import ru.rfsnab.productservice.model.Product;
import ru.rfsnab.productservice.model.ProductStock;
import ru.rfsnab.productservice.model.Warehouse;
import ru.rfsnab.productservice.repository.ProductRepository;
import ru.rfsnab.productservice.repository.ProductStockRepository;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Остатки по складам. products.stock_quantity — денормализованная сумма по активным складам
 * (свои строки + строки склеенных с товаром записей 1С), пересчитывается при каждой записи остатка.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ProductStockService {

    private final ProductStockRepository stockRepository;
    private final ProductRepository productRepository;

    @Transactional
    public void setQuantity(Product product, Warehouse warehouse, int quantity) {
        if (quantity < 0) {
            throw new BusinessException("Остаток не может быть отрицательным");
        }
        ProductStock stock = stockRepository
                .findByProductIdAndWarehouseId(product.getId(), warehouse.getId())
                .orElseGet(() -> ProductStock.builder().product(product).warehouse(warehouse).build());
        stock.setQuantity(quantity);
        stockRepository.save(stock);

        product.setStockQuantity(stockRepository.sumActive(product.getId()));
        productRepository.save(product);
        if (product.getMergedIntoProductId() != null) {
            recalcTotal(product.getMergedIntoProductId());
        }
    }

    @Transactional
    public void recalcTotal(Long productId) {
        productRepository.findById(productId).ifPresent(p -> {
            p.setStockQuantity(stockRepository.sumActive(productId));
            productRepository.save(p);
        });
    }

    @Transactional
    public void recalcAll() {
        int updated = stockRepository.recalcAllTotals();
        log.info("Пересчитаны суммарные остатки: {} товаров", updated);
    }

    /**
     * Витрина: остатки по активным складам для каждого id — свои строки + строки склеенных товаров 1С.
     * Строки с нулевым остатком не возвращаются. Один запрос на весь набор.
     */
    @Transactional(readOnly = true)
    public Map<Long, List<ProductStockResponse>> getStocks(Collection<Long> productIds) {
        if (productIds.isEmpty()) {
            return Map.of();
        }
        Set<Long> ids = new HashSet<>(productIds);
        // owner -> склад -> сумма; owner = сам товар или главный для склеенного
        Map<Long, Map<Warehouse, Integer>> byOwner = new HashMap<>();
        for (ProductStock s : stockRepository.findActiveForProducts(ids)) {
            Product p = s.getProduct();
            Long owner = ids.contains(p.getId()) ? p.getId() : p.getMergedIntoProductId();
            byOwner.computeIfAbsent(owner, k -> new HashMap<>())
                    .merge(s.getWarehouse(), s.getQuantity(), Integer::sum);
        }
        Map<Long, List<ProductStockResponse>> result = new HashMap<>();
        byOwner.forEach((owner, perWarehouse) -> result.put(owner, perWarehouse.entrySet().stream()
                .filter(e -> e.getValue() > 0)
                .sorted(Comparator.comparingInt(e -> e.getKey().getSortOrder()))
                .map(e -> WarehouseMapper.toStockResponse(e.getKey(), e.getValue()))
                .toList()));
        return result;
    }

    /** Админка: только собственные строки записи (без склеенных), включая нулевые. */
    @Transactional(readOnly = true)
    public Map<Long, List<ProductStockResponse>> getOwnStocks(Collection<Long> productIds) {
        if (productIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, List<ProductStock>> byProduct = new HashMap<>();
        for (ProductStock s : stockRepository.findByProductIdIn(productIds)) {
            byProduct.computeIfAbsent(s.getProduct().getId(), k -> new ArrayList<>()).add(s);
        }
        Map<Long, List<ProductStockResponse>> result = new HashMap<>();
        byProduct.forEach((productId, rows) -> result.put(productId, rows.stream()
                .sorted(Comparator.comparingInt(s -> s.getWarehouse().getSortOrder()))
                .map(s -> WarehouseMapper.toStockResponse(s.getWarehouse(), s.getQuantity()))
                .toList()));
        return result;
    }
}
