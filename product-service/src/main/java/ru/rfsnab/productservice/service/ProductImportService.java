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
                product.setIsActive(true);
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

            // Цена и остаток — обновляем только если переданы (могут прийти отдельно через offers.xml)
            if(item.getPrice() != null){
                product.setPrice(item.getPrice());
            }
            if(item.getStockQuantity() != null){
                product.setStockQuantity(item.getStockQuantity());
            }
            //Полная замена аттрибутов при каждом импорте
            updateAttributes(product, item.getAttributes());

            Product savedProduct = productRepository.save(product);

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
