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
import ru.rfsnab.productservice.model.ProductVariant;
import ru.rfsnab.productservice.repository.CategoryRepository;
import ru.rfsnab.productservice.repository.ProductRepository;
import ru.rfsnab.productservice.repository.ProductVariantRepository;

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
 * Сервис batch-импорта товаров из 1С CommerceML.
 * Матчинг по externalId: существующие товары обновляются, новые создаются.
 * Поле description (зона сайта) никогда не затирается при импорте.
 */
@Service
@RequiredArgsConstructor
public class ProductImportService {

    private static final String IMPORT_CATEGORY_SLUG = "import-1c";

    @Value("${product-service.import.chunk-size:25}")
    private int chunkSize;

    private final ProductRepository productRepository;
    private final ProductVariantRepository variantRepository;
    private final CategoryRepository categoryRepository;
    private final PlatformTransactionManager transactionManager;
    private final SlugGeneratorService slugService;

    /**
     * Batch-импорт товаров из 1С.
     */
    public BatchProductImportResponse importBatch(BatchProductImportRequest request){
        List<ProductImportItem> items = request.getItems();

        // Batch-загрузка данных для матчинга (один SELECT вместо N)
        Map<String, Product> existingProducts = loadExistingProducts(items);
        Set<String> reservedSlugs = ConcurrentHashMap.newKeySet();
        reservedSlugs.addAll(productRepository.findAllSlugs());

        //Дефлдтная категория для новых товаров
        Category category = categoryRepository.findBySlug(IMPORT_CATEGORY_SLUG)
                .orElseThrow(() -> new CategoryNotFoundException("Категория " + IMPORT_CATEGORY_SLUG +
                        " не найдена. Выполните миграцию в БД")
        );

        // Разбиваем на chunk'и и обрабатываем параллельно
        List<List<ProductImportItem>> chunks = partitionList(items, chunkSize);

        // Выполняем загрузку в виртуальных потоках
        try(ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()){
            List<CompletableFuture<List<ImportItemResult>>> futures = chunks.stream()
                    .map(chunk -> CompletableFuture.supplyAsync(
                            ()-> processChunk(chunk, existingProducts, reservedSlugs, category), executor
                    )).toList();

            //Ожидаем обработку всех chunk'ов
            List<ImportItemResult> allResults = futures.stream()
                    .map(CompletableFuture::join)
                    .flatMap(List::stream)
                    .toList();
            return  buildResponse(items.size(), allResults);
        }
    }

    /**
     * Обработка одного chunk'а в отдельной транзакции.
     * Если chunk падает — остальные chunk'и продолжают работу.
     */
    private List<ImportItemResult> processChunk(List<ProductImportItem> chunk,
                                                Map<String, Product> existingProducts,
                                                Set<String> reservedSlugs,
                                                Category importCategory){
        TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);

        return txTemplate.execute(status -> {
            List<ImportItemResult> results = new ArrayList<>(chunk.size());
            for(ProductImportItem item : chunk){
                results.add(processItem(item, existingProducts, reservedSlugs, importCategory));
            }
            return results;
        });
    }

    private ImportItemResult processItem(ProductImportItem item,
                                         Map<String, Product> existingProducts,
                                         Set<String> reservedSlugs,
                                         Category importCategory){
        try{
            Product product = existingProducts.get(item.getExternalId());
            boolean isNew = (product==null);
            if(isNew){
                product = new Product();
                product.setExternalId(item.getExternalId());
                product.setIsActive(false);
                product.setIsFeatured(false);
                product.setStockQuantity(0);
                product.setCategory(importCategory);
            }
            // Обновляем поля из 1С
            product.setName(item.getName());
            product.setSlug(slugService.generateUniqueSlug(product.getName(), reservedSlugs));
            product.setShortDescription(item.getShortDescription());
            product.setExternalCode(item.getExternalCode());
            product.setSku(item.getSku());
            product.setUnitOfMeasure(item.getUnitOfMeasure());
            product.setVatRate(item.getVatRate());
            if (item.getSource() != null) {
                product.setSource(item.getSource());
            }

            // Цена и остаток — обновляем только если переданы (могут прийти отдельно через offers.xml)
            if(item.getPrice() != null){
                product.setPrice(item.getPrice());
            }
            if(item.getWholesalePrice() != null){
                product.setWholesalePrice(item.getWholesalePrice());
            }
            if(item.getStockQuantity() != null){
                product.setStockQuantity(item.getStockQuantity());
            }
            //Полная замена аттрибутов при каждом импорте
            updateAttributes(product, item.getAttributes());

            Product savedProduct = productRepository.save(product);

            boolean hasExplicitVariants = item.getVariants() != null && !item.getVariants().isEmpty();
            if (hasExplicitVariants) {
                upsertExplicitVariants(savedProduct, item.getVariants());
            } else if (isNew) {
                createDefaultVariant(savedProduct, item);
            } else {
                updateDefaultVariant(savedProduct, item);
            }

            return ImportItemResult.builder()
                    .externalId(item.getExternalId())
                    .productId(savedProduct.getId())
                    .action(isNew ? ImportAction.CREATED : ImportAction.UPDATED)
                    .success(true)
                    .build();
        } catch (Exception e){
            return ImportItemResult.builder()
                    .externalId(item.getExternalId())
                    .action(ImportAction.FAILED)
                    .success(false)
                    .errorMessage(e.getMessage())
                    .build();
        }
    }

    /**
     * Создаёт или обновляет явно переданные варианты (FTK и другие поставщики с размерной сеткой).
     * Матчинг по externalId варианта. Новые — создаются, существующие — обновляются.
     */
    private void upsertExplicitVariants(Product product,
                                        List<ProductImportItem.VariantImportItem> variantItems) {
        List<String> externalIds = variantItems.stream()
                .map(ProductImportItem.VariantImportItem::getExternalId)
                .filter(id -> id != null)
                .toList();

        Map<String, ProductVariant> existing = externalIds.isEmpty()
                ? Collections.emptyMap()
                : variantRepository.findByExternalIdIn(externalIds).stream()
                        .collect(Collectors.toMap(ProductVariant::getExternalId, Function.identity()));

        for (ProductImportItem.VariantImportItem vi : variantItems) {
            if (vi.getExternalId() == null) continue;
            ProductVariant variant = existing.get(vi.getExternalId());
            if (variant == null) {
                variant = ProductVariant.builder()
                        .product(product)
                        .externalId(vi.getExternalId())
                        .isActive(true)
                        .stockQuantity(0)
                        .build();
            }
            if (vi.getSku() != null) variant.setSku(vi.getSku());
            if (vi.getPrice() != null) variant.setPrice(vi.getPrice());
            if (vi.getWholesalePrice() != null) variant.setWholesalePrice(vi.getWholesalePrice());
            if (vi.getStockQuantity() != null) variant.setStockQuantity(vi.getStockQuantity());
            if (vi.getAttributes() != null) variant.setAttributes(vi.getAttributes());
            variantRepository.save(variant);
        }
    }

    private void createDefaultVariant(Product product, ProductImportItem item) {
        String variantExternalId = item.getExternalId() != null ? item.getExternalId() + "#default" : null;
        ProductVariant variant = ProductVariant.builder()
                .product(product)
                .sku(item.getSku())
                .price(item.getPrice())
                .wholesalePrice(item.getWholesalePrice())
                .stockQuantity(item.getStockQuantity() != null ? item.getStockQuantity() : 0)
                .isActive(true)
                .externalId(variantExternalId)
                .build();
        variantRepository.save(variant);
    }

    private void updateDefaultVariant(Product product, ProductImportItem item) {
        String variantExternalId = item.getExternalId() != null ? item.getExternalId() + "#default" : null;
        if (variantExternalId == null) return;
        variantRepository.findByExternalId(variantExternalId).ifPresent(variant -> {
            if (item.getPrice() != null) variant.setPrice(item.getPrice());
            if (item.getWholesalePrice() != null) variant.setWholesalePrice(item.getWholesalePrice());
            if (item.getStockQuantity() != null) variant.setStockQuantity(item.getStockQuantity());
            if (item.getSku() != null) variant.setSku(item.getSku());
            variantRepository.save(variant);
        });
    }

    private Map<String, Product> loadExistingProducts(List<ProductImportItem> items){
        List<String> externalIds = items.stream()
                .map(ProductImportItem::getExternalId)
                .toList();

        return productRepository.findByExternalIdIn(externalIds)
                .stream()
                .collect(Collectors.toMap(Product::getExternalId, Function.identity()));
    }

    private void updateAttributes(Product product, List<ProductImportItem.ProductAttributeImportItem> attrItems){
        if(attrItems == null || attrItems.isEmpty()){
            return;
        }
        product.getAttributes().clear();

        for(ProductImportItem.ProductAttributeImportItem attrItem: attrItems){
            ProductAttribute attribute = ProductAttribute.builder()
                    .product(product)
                    .attributeName(attrItem.getName())
                    .attributeValue(attrItem.getValue())
                    .build();
            product.getAttributes().add(attribute);
        }
    }

    private BatchProductImportResponse buildResponse(int totalReceived, List<ImportItemResult> results){
        int created =0;
        int updated =0;
        int failed =0;

        for(ImportItemResult result : results){
            switch (result.getAction()){
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

    /**
     * Разбивает списки на chunk'и указанного размера
     */
    private <T> List<List<T>> partitionList(List<T> list,int chunkSize){
        List<List<T>> partitions = new ArrayList<>();
        for (int i = 0; i < list.size(); i += chunkSize) {
            partitions.add(list.subList(i, Math.min(i + chunkSize, list.size())));
        }
        return partitions;
    }
}
