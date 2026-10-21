package ru.rfsnab.productservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import ru.rfsnab.productservice.configuration.CacheConfig;
import ru.rfsnab.productservice.exception.BusinessException;
import ru.rfsnab.productservice.exception.ProductNotFoundException;
import ru.rfsnab.productservice.model.Product;
import ru.rfsnab.productservice.repository.ProductRepository;

import java.util.List;

/**
 * Склейка товара 1С с товаром Факела по sku. Склеенный товар 1С скрыт (isActive=false)
 * и ссылается на главный через mergedIntoProductId; его остаток остаётся на его же строке,
 * главный товар агрегирует остатки (ProductStockService).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ProductMergeService {

    public static final String SOURCE_FTK = "FTK";
    public static final String SOURCE_ONEC = "INTERNAL";

    private final ProductRepository productRepository;
    private final ProductStockService stockService;

    /** Автосклейка из импорта 1С: ровно одна пара Факела по точному sku. */
    @Transactional
    public void autoMergeOneC(Product oneC) {
        if (!isMergeableOneC(oneC) || !StringUtils.hasText(oneC.getSku())) {
            return;
        }
        List<Product> candidates = productRepository.findMergeableFtkBySku(oneC.getSku());
        if (candidates.size() == 1) {
            link(candidates.get(0), oneC);
        }
    }

    /** Автосклейка из импорта Факела: ровно один несклеенный товар 1С по точному sku. */
    @Transactional
    public void autoMergeFtk(Product ftk) {
        if (!StringUtils.hasText(ftk.getSku())) {
            return;
        }
        List<Product> candidates = productRepository.findMergeableOneCBySku(ftk.getSku());
        if (candidates.size() == 1) {
            link(ftk, candidates.get(0));
        }
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(value = CacheConfig.FACETS_CACHE, allEntries = true),
            @CacheEvict(value = CacheConfig.PRODUCT_STATS_CACHE, allEntries = true)
    })
    public void merge(Long mainId, Long oneCId) {
        Product main = require(mainId);
        Product oneC = require(oneCId);
        if (!SOURCE_FTK.equals(main.getSource())) {
            throw new BusinessException("Главным может быть только товар Факела");
        }
        if (productRepository.existsByParentProductId(mainId)) {
            throw new BusinessException("Нельзя привязать к товару с вариантами — привяжите к варианту");
        }
        if (!isOneC(oneC)) {
            throw new BusinessException("Привязать можно только товар 1С");
        }
        if (oneC.getMergedIntoProductId() != null) {
            throw new BusinessException("Товар уже склеен с товаром id=" + oneC.getMergedIntoProductId());
        }
        oneC.setMergeLocked(false);
        link(main, oneC);
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(value = CacheConfig.FACETS_CACHE, allEntries = true),
            @CacheEvict(value = CacheConfig.PRODUCT_STATS_CACHE, allEntries = true)
    })
    public void unmerge(Long oneCId) {
        Product oneC = require(oneCId);
        Long mainId = oneC.getMergedIntoProductId();
        if (mainId == null) {
            throw new BusinessException("Товар не склеен");
        }
        oneC.setMergedIntoProductId(null);
        oneC.setMergeLocked(true);
        productRepository.save(oneC);
        stockService.recalcTotal(mainId);
        log.info("Склейка снята: 1С id={} <- главный id={}", oneCId, mainId);
    }

    @Transactional(readOnly = true)
    public List<Product> getMerged(Long mainId) {
        return productRepository.findByMergedIntoProductIdInOrderByIdAsc(List.of(mainId));
    }

    @Transactional(readOnly = true)
    public Page<Product> findCandidates(String query, Pageable pageable) {
        return productRepository.findMergeCandidates(query, pageable);
    }

    private void link(Product main, Product oneC) {
        oneC.setMergedIntoProductId(main.getId());
        oneC.setIsActive(false);
        productRepository.save(oneC);
        stockService.recalcTotal(main.getId());
        log.info("Склейка: 1С id={} sku={} -> главный id={}", oneC.getId(), oneC.getSku(), main.getId());
    }

    private boolean isOneC(Product p) {
        return SOURCE_ONEC.equals(p.getSource()) && p.getExternalId() != null;
    }

    private boolean isMergeableOneC(Product p) {
        return isOneC(p) && p.getMergedIntoProductId() == null && !Boolean.TRUE.equals(p.getMergeLocked());
    }

    private Product require(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new ProductNotFoundException(id));
    }
}
