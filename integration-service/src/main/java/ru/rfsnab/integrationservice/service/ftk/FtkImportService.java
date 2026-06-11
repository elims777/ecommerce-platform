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
import ru.rfsnab.integrationservice.service.ftk.FtkFtpClient.FtpStreamHandle;
import ru.rfsnab.integrationservice.service.ftk.FtkXmlParser.OfferData;
import ru.rfsnab.integrationservice.service.ftk.FtkXmlParser.ProductData;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Оркестратор импорта ФТК.
 *
 * importFromXls() — устаревший метод через XLS-файл, сохранён для обратной совместимости.
 * importFromFtp() — актуальный метод: скачивает XML с FTP и парсит через FtkXmlParser.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FtkImportService {

    private static final String SOURCE           = "FTK";
    private static final String BATCH_IMPORT_URI = "/api/v1/products/import/batch";

    private final FtkXlsParser xlsParser;
    private final FtkXmlParser xmlParser;
    private final FtkFtpClient ftpClient;
    private final FtkCategoryMapper categoryMapper;
    private final FtkImageDownloader imageDownloader;
    private final RestTemplate productServiceRestTemplate;
    private final IntegrationProperties properties;

    // ══════════════════════════════════════════════════════════════
    // Актуальный метод: XML с FTP
    // ══════════════════════════════════════════════════════════════

    /**
     * Полный импорт каталога ФТК с FTP-сервера: скачивает 5 XML-файлов, парсит,
     * загружает товары в product-service, скачивает изображения.
     */
    public FtkImportResult importFromFtp() throws Exception {
        IntegrationProperties.FtkProperties cfg = properties.getFtk();
        categoryMapper.resetCache();
        String goodsDir = ftpClient.getGoodsDir();
        String rootDir  = ftpClient.getRootDir();

        log.info("ФТК XML импорт запущен");

        // 1. Корневой классификатор → словарь групп
        String rootImportPath = ftpClient.findFileByPrefix(rootDir, "import___");
        if (rootImportPath == null) throw new IOException("Корневой import___.xml не найден на FTP");
        Map<String, String> groupPaths;
        try (InputStream is = ftpClient.openStream(rootImportPath)) {
            groupPaths = xmlParser.parseClassifier(is);
        }

        // 2. Товары из goods/1/import
        String goodsImportPath = ftpClient.findFileByPrefix(goodsDir, "import___");
        if (goodsImportPath == null) throw new IOException("goods/1/import___.xml не найден на FTP");
        Map<String, ProductData> products;
        try (InputStream is = ftpClient.openStream(goodsImportPath)) {
            products = xmlParser.parseProducts(is, groupPaths);
        }

        // 3. Предложения (offers)
        String offersPath = ftpClient.findFileByPrefix(goodsDir, "offers___");
        if (offersPath == null) throw new IOException("offers___.xml не найден на FTP");
        Map<String, OfferData> offers;
        try (InputStream is = ftpClient.openStream(offersPath)) {
            offers = xmlParser.parseOffers(is);
        }

        // 4. Цены — StAX стрим (125 МБ)
        String pricesPath = ftpClient.findFileByPrefix(goodsDir, "prices___");
        if (pricesPath == null) throw new IOException("prices___.xml не найден на FTP");
        Map<String, BigDecimal> prices;
        try (FtpStreamHandle handle = ftpClient.openLargeStream(pricesPath)) {
            prices = xmlParser.parsePrices(handle.getStream());
        }

        // 5. Остатки
        String restsPath = ftpClient.findFileByPrefix(goodsDir, "rests___");
        if (restsPath == null) throw new IOException("rests___.xml не найден на FTP");
        Map<String, Integer> rests;
        try (InputStream is = ftpClient.openStream(restsPath)) {
            rests = xmlParser.parseRests(is);
        }

        // 6. Сборка
        List<FtkProduct> allProducts = xmlParser.assemble(products, offers, prices, rests);
        log.info("ФТК: собрано {} товаров", allProducts.size());

        // 7. Лимит
        int limit = cfg.getImportLimit();
        List<FtkProduct> limited = (limit > 0 && allProducts.size() > limit)
                ? allProducts.subList(0, limit)
                : allProducts;
        if (limit > 0 && allProducts.size() > limit) {
            log.info("ФТК: применён лимит {} из {}", limit, allProducts.size());
        }

        // 8. Маппинг в DTO и batch-импорт
        List<ProductImportItemDto> dtos = limited.stream()
                .map(this::mapToDto)
                .filter(dto -> dto != null)
                .toList();

        BatchImportResult batchResult = sendBatch(dtos);
        log.info("ФТК batch-импорт: created={}, updated={}, failed={}",
                batchResult.created(), batchResult.updated(), batchResult.failed());

        // 9. Изображения
        int imagesOk = 0, imagesFailed = 0;
        for (FtkProduct p : limited) {
            if (p.getImagePath() == null) continue;
            // Полный FTP путь: /webdata/000000003/goods/1/ + относительный путь из XML
            String ftpImagePath = "ftp://" + properties.getFtk().getFtp().getHost()
                    + goodsDir + p.getImagePath();
            String externalId = buildProductExternalId(p.getArticle());
            boolean ok = imageDownloader.downloadAndUpload(ftpImagePath, externalId);
            if (ok) imagesOk++; else imagesFailed++;
        }
        log.info("ФТК изображения: ok={}, failed={}", imagesOk, imagesFailed);

        return new FtkImportResult(limited.size(), batchResult.created(),
                batchResult.updated(), batchResult.failed(), imagesOk, imagesFailed);
    }

    // ══════════════════════════════════════════════════════════════
    // Устаревший метод: XLS
    // ══════════════════════════════════════════════════════════════

    /**
     * @deprecated Используй importFromFtp(). XLS делался для другого клиента ФТК.
     */
    @Deprecated
    public FtkImportResult importFromXls(InputStream xls) throws IOException {
        IntegrationProperties.FtkProperties cfg = properties.getFtk();
        categoryMapper.resetCache();

        List<FtkProduct> allProducts = xlsParser.parse(xls);
        log.info("ФТК XLS: распарсено {} товаров", allProducts.size());

        int limit = cfg.getImportLimit();
        List<FtkProduct> products = (limit > 0 && allProducts.size() > limit)
                ? allProducts.subList(0, limit)
                : allProducts;

        List<ProductImportItemDto> importItems = products.stream()
                .map(this::mapToDto)
                .filter(dto -> dto != null)
                .toList();

        BatchImportResult batchResult = sendBatch(importItems);
        log.info("ФТК XLS batch-импорт: created={}, updated={}, failed={}",
                batchResult.created(), batchResult.updated(), batchResult.failed());

        int imagesOk = 0, imagesFailed = 0;
        for (FtkProduct p : products) {
            String externalId = buildProductExternalId(p.getArticle());
            if (p.getImagePath() != null) {
                boolean ok = imageDownloader.downloadAndUpload(p.getImagePath(), externalId);
                if (ok) imagesOk++; else imagesFailed++;
            }
        }

        return new FtkImportResult(products.size(), batchResult.created(),
                batchResult.updated(), batchResult.failed(), imagesOk, imagesFailed);
    }

    // ══════════════════════════════════════════════════════════════
    // Маппинг
    // ══════════════════════════════════════════════════════════════

    private ProductImportItemDto mapToDto(FtkProduct p) {
        if (p == null || p.getArticle() == null || p.getName() == null) return null;

        String externalId    = buildProductExternalId(p.getArticle());
        String categorySlug  = categoryMapper.resolveSlug(p.getCategoryPath());

        // Базовая цена товара: берём из первого варианта
        BigDecimal basePrice = p.getVariants().stream()
                .map(FtkVariant::getPrice)
                .filter(pr -> pr != null)
                .findFirst()
                .orElse(null);

        Integer vatRate = p.getVariants().stream()
                .map(FtkVariant::getVatRate)
                .filter(v -> v != null)
                .findFirst()
                .orElse(null);

        ProductImportItemDto dto = ProductImportItemDto.builder()
                .externalId(externalId)
                .name(p.getName())
                .sku(p.getArticle())
                .description(p.getDescription())
                .price(basePrice)
                .wholesalePrice(basePrice)   // FTK: нет отдельной оптовой цены
                .vatRate(vatRate)
                .source(SOURCE)
                .build();

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
                .wholesalePrice(price)
                .stockQuantity(v.getStockQuantity())
                .attributes(v.getAttributes())
                .build();
    }

    private String buildProductExternalId(String article) {
        return "FTK-" + article;
    }

    private String buildVariantExternalId(String article) {
        return "FTK-" + article;
    }

    // ══════════════════════════════════════════════════════════════
    // Batch send
    // ══════════════════════════════════════════════════════════════

    private BatchImportResult sendBatch(List<ProductImportItemDto> items) {
        if (items.isEmpty()) return new BatchImportResult(0, 0, 0);

        int chunkSize = properties.getImportConfig().getChunkSize();
        int created = 0, updated = 0, failed = 0;

        for (int i = 0; i < items.size(); i += chunkSize) {
            List<ProductImportItemDto> chunk = items.subList(i, Math.min(i + chunkSize, items.size()));
            try {
                String url = properties.getProductService().getUrl() + BATCH_IMPORT_URI;
                ResponseEntity<BatchImportResponse> resp = productServiceRestTemplate.postForEntity(
                        url, new BatchImportRequest(chunk), BatchImportResponse.class);
                BatchImportResponse body = resp.getBody();
                if (body != null) {
                    created += body.getCreated();
                    updated += body.getUpdated();
                    failed  += body.getFailed();
                }
            } catch (Exception e) {
                log.error("Ошибка отправки chunk [{}-{}]: {}", i, i + chunk.size(), e.getMessage());
                failed += chunk.size();
            }
        }

        return new BatchImportResult(created, updated, failed);
    }

    // ══════════════════════════════════════════════════════════════
    // Result types
    // ══════════════════════════════════════════════════════════════

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
