package ru.rfsnab.productservice.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import ru.rfsnab.productservice.dto.BatchProductImportRequest;
import ru.rfsnab.productservice.dto.BatchProductImportResponse;
import ru.rfsnab.productservice.dto.BatchProductImportResponse.ImportAction;
import ru.rfsnab.productservice.dto.BatchProductImportResponse.ImportItemResult;
import ru.rfsnab.productservice.dto.ProductImportItem;
import ru.rfsnab.productservice.exception.CategoryNotFoundException;
import ru.rfsnab.productservice.model.Category;
import ru.rfsnab.productservice.model.Product;
import ru.rfsnab.productservice.model.ProductAttribute;
import ru.rfsnab.productservice.repository.CategoryRepository;
import ru.rfsnab.productservice.repository.ProductRepository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Сервис batch-импорта товаров из 1С CommerceML и ФТК.
 * Матчинг по externalId: существующие товары обновляются, новые создаются.
 * Поле description (зона сайта) никогда не затирается при импорте.
 * Варианты (размерная сетка ФТК) создаются как дочерние записи в таблице products.
 */
@Service
@RequiredArgsConstructor
public class ProductImportService {

    private static final String IMPORT_CATEGORY_SLUG = "import-1c";

    @Value("${product-service.import.chunk-size:25}")
    private int chunkSize;

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final PlatformTransactionManager transactionManager;
    private final SlugGeneratorService slugService;

    public BatchProductImportResponse importBatch(BatchProductImportRequest request) {
        List<ProductImportItem> items = request.getItems();

        Map<String, Product> existingProducts = loadExistingProducts(items);
        Set<String> reservedSlugs = ConcurrentHashMap.newKeySet();
        reservedSlugs.addAll(productRepository.findAllSlugs());

        Category importCategory = categoryRepository.findBySlug(IMPORT_CATEGORY_SLUG)
                .orElseThrow(() -> new CategoryNotFoundException(
                        "Категория " + IMPORT_CATEGORY_SLUG + " не найдена. Выполните миграцию в БД"));

        List<List<ProductImportItem>> chunks = partitionList(items, chunkSize);

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<CompletableFuture<List<ImportItemResult>>> futures = chunks.stream()
                    .map(chunk -> CompletableFuture.supplyAsync(
                            () -> processChunk(chunk, existingProducts, reservedSlugs, importCategory), executor))
                    .toList();

            List<ImportItemResult> allResults = futures.stream()
                    .map(CompletableFuture::join)
                    .flatMap(List::stream)
                    .toList();

            return buildResponse(items.size(), allResults);
        }
    }

    private List<ImportItemResult> processChunk(List<ProductImportItem> chunk,
                                                Map<String, Product> existingProducts,
                                                Set<String> reservedSlugs,
                                                Category importCategory) {
        TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);
        return txTemplate.execute(status -> {
            List<ImportItemResult> results = new ArrayList<>(chunk.size());
            for (ProductImportItem item : chunk) {
                results.add(processItem(item, existingProducts, reservedSlugs, importCategory));
            }
            return results;
        });
    }

    private ImportItemResult processItem(ProductImportItem item,
                                         Map<String, Product> existingProducts,
                                         Set<String> reservedSlugs,
                                         Category importCategory) {
        try {
            Product product = existingProducts.get(item.getExternalId());
            boolean isNew = (product == null);
            if (isNew) {
                product = new Product();
                product.setExternalId(item.getExternalId());
                product.setIsActive(false);
                product.setIsFeatured(false);
                product.setStockQuantity(0);
                Category resolvedCategory = item.getCategoryId() != null
                        ? categoryRepository.findById(item.getCategoryId()).orElse(importCategory)
                        : importCategory;
                product.setCategory(resolvedCategory);
            }

            product.setName(item.getName());
            product.setSlug(slugService.generateUniqueSlug(product.getName(), reservedSlugs));
            product.setShortDescription(item.getShortDescription());
            if (item.getDescription() != null) {
                product.setDescription(item.getDescription());
            }
            if (item.getMaterial() != null) {
                product.setMaterial(item.getMaterial());
            }
            product.setExternalCode(item.getExternalCode());
            product.setSku(item.getSku());
            product.setUnitOfMeasure(item.getUnitOfMeasure());
            product.setVatRate(item.getVatRate());
            if (item.getSource() != null) {
                product.setSource(item.getSource());
            }
            if (item.getBarcode() != null) {
                product.setBarcode(item.getBarcode());
            }
            if (item.getCountryOfOrigin() != null) {
                product.setCountryOfOrigin(item.getCountryOfOrigin());
            }
            if (item.getPrice() != null) {
                product.setPrice(item.getPrice());
            }
            if (item.getWholesalePrice() != null) {
                product.setWholesalePrice(item.getWholesalePrice());
            }
            if (item.getStockQuantity() != null) {
                product.setStockQuantity(item.getStockQuantity());
            }

            updateAttributes(product, item.getAttributes());

            Product savedProduct = productRepository.save(product);

            if (item.getVariants() != null && !item.getVariants().isEmpty()) {
                upsertChildVariants(savedProduct, item.getVariants(), existingProducts, reservedSlugs, importCategory);
            }

            return ImportItemResult.builder()
                    .externalId(item.getExternalId())
                    .productId(savedProduct.getId())
                    .action(isNew ? ImportAction.CREATED : ImportAction.UPDATED)
                    .success(true)
                    .build();
        } catch (Exception e) {
            return ImportItemResult.builder()
                    .externalId(item.getExternalId())
                    .action(ImportAction.FAILED)
                    .success(false)
                    .errorMessage(e.getMessage())
                    .build();
        }
    }

    /**
     * Создаёт или обновляет варианты как дочерние записи Product.
     * Матчинг по externalId варианта. Новые создаются с isVariantChild=true.
     */
    private void upsertChildVariants(Product parent,
                                     List<ProductImportItem.VariantImportItem> variantItems,
                                     Map<String, Product> existingProducts,
                                     Set<String> reservedSlugs,
                                     Category importCategory) {
        List<String> externalIds = variantItems.stream()
                .map(ProductImportItem.VariantImportItem::getExternalId)
                .filter(id -> id != null)
                .toList();

        Map<String, Product> existingChildren = externalIds.isEmpty()
                ? Collections.emptyMap()
                : productRepository.findByExternalIdIn(externalIds).stream()
                        .collect(Collectors.toMap(Product::getExternalId, Function.identity()));

        for (ProductImportItem.VariantImportItem vi : variantItems) {
            if (vi.getExternalId() == null) continue;

            Product child = existingChildren.get(vi.getExternalId());
            boolean isNew = (child == null);
            if (isNew) {
                child = new Product();
                child.setExternalId(vi.getExternalId());
                child.setIsActive(false);
                child.setIsFeatured(false);
                child.setStockQuantity(0);
                child.setIsVariantChild(true);
                child.setParentProductId(parent.getId());
                child.setCategory(parent.getCategory() != null ? parent.getCategory() : importCategory);
                child.setSource(parent.getSource());
            }

            if (vi.getSku() != null) child.setSku(vi.getSku());
            child.setName(parent.getName() + (vi.getSku() != null ? " (" + vi.getSku() + ")" : ""));
            if (isNew) {
                child.setSlug(slugService.generateUniqueSlug(child.getName(), reservedSlugs));
            }
            if (vi.getPrice() != null) child.setPrice(vi.getPrice());
            if (vi.getWholesalePrice() != null) child.setWholesalePrice(vi.getWholesalePrice());
            if (vi.getStockQuantity() != null) child.setStockQuantity(vi.getStockQuantity());
            if (vi.getBarcode() != null) child.setBarcode(vi.getBarcode());
            if (vi.getCountryOfOrigin() != null) child.setCountryOfOrigin(vi.getCountryOfOrigin());

            if (vi.getAttributes() != null && !vi.getAttributes().isEmpty()) {
                List<ProductImportItem.ProductAttributeImportItem> attrs = vi.getAttributes().entrySet().stream()
                        .map(e -> new ProductImportItem.ProductAttributeImportItem(e.getKey(), e.getValue()))
                        .toList();
                updateAttributes(child, attrs);
            }

            productRepository.save(child);
        }
    }

    private Map<String, Product> loadExistingProducts(List<ProductImportItem> items) {
        List<String> externalIds = items.stream()
                .map(ProductImportItem::getExternalId)
                .toList();
        return productRepository.findByExternalIdIn(externalIds).stream()
                .collect(Collectors.toMap(Product::getExternalId, Function.identity()));
    }

    private void updateAttributes(Product product, List<ProductImportItem.ProductAttributeImportItem> attrItems) {
        if (attrItems == null || attrItems.isEmpty()) {
            return;
        }
        product.getAttributes().clear();
        for (ProductImportItem.ProductAttributeImportItem attrItem : attrItems) {
            ProductAttribute attribute = ProductAttribute.builder()
                    .product(product)
                    .attributeName(attrItem.getName())
                    .attributeValue(attrItem.getValue())
                    .build();
            product.getAttributes().add(attribute);
        }
    }

    private BatchProductImportResponse buildResponse(int totalReceived, List<ImportItemResult> results) {
        int created = 0, updated = 0, failed = 0;
        for (ImportItemResult result : results) {
            switch (result.getAction()) {
                case CREATED -> created++;
                case UPDATED -> updated++;
                case FAILED -> failed++;
            }
        }
        return BatchProductImportResponse.builder()
                .totalReceived(totalReceived)
                .created(created)
                .updated(updated)
                .failed(failed)
                .results(results)
                .build();
    }

    private <T> List<List<T>> partitionList(List<T> list, int chunkSize) {
        List<List<T>> partitions = new ArrayList<>();
        for (int i = 0; i < list.size(); i += chunkSize) {
            partitions.add(list.subList(i, Math.min(i + chunkSize, list.size())));
        }
        return partitions;
    }
}
