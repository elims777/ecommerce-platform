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

import java.math.BigDecimal;
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
            ProductSnapshot beforeSnapshot = isNew ? null : ProductSnapshot.of(product);

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
                product.setSlug(slugService.generateUniqueSlug(item.getName(), reservedSlugs));
            }

            product.setName(item.getName());
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

            boolean attributesChanged = !isNew && attributesChanged(product, item.getAttributes());
            updateAttributes(product, item.getAttributes());

            Product savedProduct = productRepository.save(product);

            boolean childrenChanged = false;
            if (item.getVariants() != null && !item.getVariants().isEmpty()) {
                childrenChanged = upsertChildVariants(savedProduct, item.getVariants(), existingProducts, reservedSlugs, importCategory);
            }

            ImportAction action;
            if (isNew) {
                action = ImportAction.CREATED;
            } else {
                boolean fieldsChanged = !beforeSnapshot.equals(ProductSnapshot.of(savedProduct));
                action = (fieldsChanged || attributesChanged || childrenChanged)
                        ? ImportAction.UPDATED
                        : ImportAction.UNCHANGED;
            }

            return ImportItemResult.builder()
                    .externalId(item.getExternalId())
                    .productId(savedProduct.getId())
                    .action(action)
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
     *
     * @return true, если хотя бы один вариант был создан или реально изменён
     *         (используется для честного счётчика "обновлено" родительского товара)
     */
    private boolean upsertChildVariants(Product parent,
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

        boolean anyChanged = false;

        for (ProductImportItem.VariantImportItem vi : variantItems) {
            if (vi.getExternalId() == null) continue;

            Product child = existingChildren.get(vi.getExternalId());
            boolean isNew = (child == null);
            ProductSnapshot beforeSnapshot = isNew ? null : ProductSnapshot.of(child);

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
            child.setUnitOfMeasure(parent.getUnitOfMeasure());
            child.setName(buildVariantName(parent.getName(), vi));
            if (isNew) {
                child.setSlug(slugService.generateUniqueSlug(child.getName(), reservedSlugs));
            }
            if (vi.getPrice() != null) child.setPrice(vi.getPrice());
            if (vi.getWholesalePrice() != null) child.setWholesalePrice(vi.getWholesalePrice());
            if (vi.getStockQuantity() != null) child.setStockQuantity(vi.getStockQuantity());
            if (vi.getBarcode() != null) child.setBarcode(vi.getBarcode());
            if (vi.getCountryOfOrigin() != null) child.setCountryOfOrigin(vi.getCountryOfOrigin());

            List<ProductImportItem.ProductAttributeImportItem> attrs = (vi.getAttributes() != null && !vi.getAttributes().isEmpty())
                    ? vi.getAttributes().entrySet().stream()
                            .map(e -> new ProductImportItem.ProductAttributeImportItem(e.getKey(), e.getValue()))
                            .toList()
                    : null;
            boolean attributesChanged = !isNew && attributesChanged(child, attrs);
            updateAttributes(child, attrs);

            Product savedChild = productRepository.save(child);

            if (isNew || attributesChanged || !beforeSnapshot.equals(ProductSnapshot.of(savedChild))) {
                anyChanged = true;
            }
        }

        return anyChanged;
    }

    /**
     * Имя варианта: "{имя родителя} ({размер}, {цвет})".
     * Размер и цвет берутся из атрибутов варианта; цвет добавляется, только если его
     * ещё нет в имени родителя (чтобы не дублировать). Если ни размера, ни цвета нет —
     * fallback на артикул, как раньше.
     */
    private String buildVariantName(String parentName, ProductImportItem.VariantImportItem vi) {
        Map<String, String> attrs = vi.getAttributes() != null ? vi.getAttributes() : Map.of();
        List<String> parts = new ArrayList<>();

        String size = attrs.get("Размер");
        if (size != null && !size.isBlank()) {
            parts.add("размер " + size.trim());
        }

        String color = attrs.getOrDefault("Основной цвет", attrs.get("Цвет"));
        if (color != null && !color.isBlank()
                && !parentName.toLowerCase().contains(color.trim().toLowerCase())) {
            parts.add(color.trim());
        }

        if (!parts.isEmpty()) {
            return parentName + " (" + String.join(", ", parts) + ")";
        }
        return parentName + (vi.getSku() != null ? " (" + vi.getSku() + ")" : "");
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

    /**
     * Сравнивает текущие атрибуты товара с атрибутами из импорта по значению (имя+значение),
     * не по identity — иначе updateAttributes() (clear + пересоздание) всегда бы считался изменением.
     * Если attrItems==null/пусто — updateAttributes() ничего не делает, значит изменений нет.
     */
    private boolean attributesChanged(Product product, List<ProductImportItem.ProductAttributeImportItem> attrItems) {
        if (attrItems == null || attrItems.isEmpty()) {
            return false;
        }
        Set<String> currentPairs = product.getAttributes().stream()
                .map(a -> a.getAttributeName() + " " + a.getAttributeValue())
                .collect(Collectors.toSet());
        Set<String> newPairs = attrItems.stream()
                .map(a -> a.getName() + " " + a.getValue())
                .collect(Collectors.toSet());
        return !currentPairs.equals(newPairs);
    }

    /**
     * Снимок значимых полей товара, маппящихся из импорта (1С/ФТК).
     * Используется для честного счётчика "обновлено": если снимок до и после
     * применения импортируемых данных совпадает — товар не менялся (UNCHANGED).
     * Slug сознательно не входит в сравнение.
     */
    private record ProductSnapshot(
            String name,
            String shortDescription,
            String description,
            String material,
            String externalCode,
            String sku,
            String unitOfMeasure,
            Integer vatRate,
            String source,
            String barcode,
            String countryOfOrigin,
            BigDecimal price,
            BigDecimal wholesalePrice,
            Integer stockQuantity,
            Long categoryId
    ) {
        static ProductSnapshot of(Product p) {
            return new ProductSnapshot(
                    p.getName(),
                    p.getShortDescription(),
                    p.getDescription(),
                    p.getMaterial(),
                    p.getExternalCode(),
                    p.getSku(),
                    p.getUnitOfMeasure(),
                    p.getVatRate(),
                    p.getSource(),
                    p.getBarcode(),
                    p.getCountryOfOrigin(),
                    normalize(p.getPrice()),
                    normalize(p.getWholesalePrice()),
                    p.getStockQuantity(),
                    p.getCategory() != null ? p.getCategory().getId() : null
            );
        }

        // BigDecimal.equals() чувствителен к scale (8000 != 8000.00) — нормализуем перед сравнением
        private static BigDecimal normalize(BigDecimal value) {
            return value != null ? value.stripTrailingZeros() : null;
        }
    }

    private BatchProductImportResponse buildResponse(int totalReceived, List<ImportItemResult> results) {
        int created = 0, updated = 0, unchanged = 0, failed = 0;
        for (ImportItemResult result : results) {
            switch (result.getAction()) {
                case CREATED -> created++;
                case UPDATED -> updated++;
                case UNCHANGED -> unchanged++;
                case FAILED -> failed++;
            }
        }
        return BatchProductImportResponse.builder()
                .totalReceived(totalReceived)
                .created(created)
                .updated(updated)
                .unchanged(unchanged)
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
