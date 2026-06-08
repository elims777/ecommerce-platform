package ru.rfsnab.integrationservice.service.ftk;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import ru.rfsnab.integrationservice.config.IntegrationProperties;
import ru.rfsnab.integrationservice.dto.BatchImportRequest;
import ru.rfsnab.integrationservice.dto.BatchImportResponse;
import ru.rfsnab.integrationservice.dto.ProductImportItemDto;
import ru.rfsnab.integrationservice.dto.ProductImportItemDto.VariantImportItemDto;
import ru.rfsnab.integrationservice.model.ftk.FtkProduct;
import ru.rfsnab.integrationservice.model.ftk.FtkProduct.FtkVariant;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Оркестратор импорта ФТК:
 * 1. FtkXlsParser: XLS → List<FtkProduct>
 * 2. Обрезка по лимиту
 * 3. FtkCategoryMapper: маппинг categoryPath → slug
 * 4. Маппинг FtkProduct → ProductImportItemDto (с вариантами)
 * 5. BatchImport в product-service
 * 6. FtkImageDownloader: скачать и загрузить изображения
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FtkImportService {

    private static final String SOURCE = "FTK";
    private static final String BATCH_IMPORT_URI = "/api/v1/products/import/batch";

    private final FtkXlsParser xlsParser;
    private final FtkCategoryMapper categoryMapper;
    private final FtkImageDownloader imageDownloader;
    private final RestTemplate productServiceRestTemplate;
    private final IntegrationProperties properties;

    /**
     * Запускает импорт из XLS потока.
     *
     * @param xls входной поток XLS-файла
     * @return сводка результата
     */
    public FtkImportResult importFromXls(InputStream xls) throws IOException {
        IntegrationProperties.FtkProperties cfg = properties.getFtk();
        categoryMapper.resetCache();

        // 1. Парсинг
        List<FtkProduct> allProducts = xlsParser.parse(xls);
        log.info("ФТК: распарсено {} товаров из XLS", allProducts.size());

        // 2. Лимит
        int limit = cfg.getImportLimit();
        List<FtkProduct> products = (limit > 0 && allProducts.size() > limit)
                ? allProducts.subList(0, limit)
                : allProducts;
        if (limit > 0 && allProducts.size() > limit) {
            log.info("ФТК: применён лимит {} из {} товаров", limit, allProducts.size());
        }

        // 3. Маппинг в DTO
        List<ProductImportItemDto> importItems = products.stream()
                .map(this::mapToDto)
                .filter(dto -> dto != null)
                .toList();

        // 4. Batch import
        BatchImportResult batchResult = sendBatch(importItems);
        log.info("ФТК batch-импорт: created={}, updated={}, failed={}",
                batchResult.created(), batchResult.updated(), batchResult.failed());

        // 5. Изображения (только для успешно импортированных)
        int imagesOk = 0;
        int imagesFailed = 0;
        for (FtkProduct p : products) {
            String externalId = buildProductExternalId(p.getArticle());
            // Картинку грузим только один раз для родителя
            if (p.getImageUrl() != null) {
                boolean ok = imageDownloader.downloadAndUpload(p.getImageUrl(), externalId);
                if (ok) imagesOk++; else imagesFailed++;
            }
        }
        log.info("ФТК изображения: ok={}, failed={}", imagesOk, imagesFailed);

        return new FtkImportResult(
                products.size(),
                batchResult.created(),
                batchResult.updated(),
                batchResult.failed(),
                imagesOk,
                imagesFailed
        );
    }

    // ===== Mapping =====

    private ProductImportItemDto mapToDto(FtkProduct p) {
        if (p == null || p.getArticle() == null || p.getName() == null) return null;

        String externalId = buildProductExternalId(p.getArticle());
        String categorySlug = categoryMapper.resolveSlug(p.getCategoryPath());

        // Определяем базовую цену товара — берём из первого варианта или из самого товара
        BigDecimal basePrice = p.getPrice();
        if (basePrice == null && !p.getVariants().isEmpty()) {
            basePrice = p.getVariants().get(0).getPrice();
        }

        // У ФТК только розничная цена — wholesalePrice = price
        ProductImportItemDto dto = ProductImportItemDto.builder()
                .externalId(externalId)
                .name(p.getName())
                .sku(p.getArticle())
                .price(basePrice)
                .wholesalePrice(basePrice)   // FTK: нет оптовой цены
                .source(SOURCE)
                .build();

        // Варианты
        if (!p.getVariants().isEmpty()) {
            List<VariantImportItemDto> variantDtos = p.getVariants().stream()
                    .map(v -> mapVariant(v, externalId))
                    .toList();
            dto.setVariants(variantDtos);
        }

        return dto;
    }

    private VariantImportItemDto mapVariant(FtkVariant v, String parentExternalId) {
        BigDecimal price = v.getPrice();
        return VariantImportItemDto.builder()
                .externalId(buildVariantExternalId(v.getArticle()))
                .sku(v.getArticle())
                .price(price)
                .wholesalePrice(price)   // FTK: нет оптовой цены
                .stockQuantity(0)        // остатки в XLS не указаны
                .attributes(v.getAttributes())
                .build();
    }

    /**
     * externalId товара — артикул с префиксом "FTK-" для изоляции от 1С UUID.
     */
    private String buildProductExternalId(String article) {
        return "FTK-" + article;
    }

    private String buildVariantExternalId(String article) {
        return "FTK-" + article;
    }

    // ===== Batch send =====

    private BatchImportResult sendBatch(List<ProductImportItemDto> items) {
        if (items.isEmpty()) return new BatchImportResult(0, 0, 0);

        int chunkSize = properties.getImportConfig().getChunkSize();
        int created = 0, updated = 0, failed = 0;

        for (int i = 0; i < items.size(); i += chunkSize) {
            List<ProductImportItemDto> chunk = items.subList(i, Math.min(i + chunkSize, items.size()));
            try {
                String url = properties.getProductService().getUrl() + BATCH_IMPORT_URI;
                ResponseEntity<BatchImportResponse> resp = productServiceRestTemplate.postForEntity(
                        url, new BatchImportRequest(chunk), BatchImportResponse.class
                );
                BatchImportResponse body = resp.getBody();
                if (body != null) {
                    created += body.getCreated();
                    updated += body.getUpdated();
                    failed += body.getFailed();
                }
            } catch (Exception e) {
                log.error("Ошибка отправки chunk [{}-{}] в product-service: {}", i, i + chunk.size(), e.getMessage());
                failed += chunk.size();
            }
        }

        return new BatchImportResult(created, updated, failed);
    }

    // ===== Result types =====

    private record BatchImportResult(int created, int updated, int failed) {}

    public record FtkImportResult(
            int totalProducts,
            int created,
            int updated,
            int failed,
            int imagesOk,
            int imagesFailed
    ) {}
}
