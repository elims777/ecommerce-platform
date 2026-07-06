package ru.rfsnab.integrationservice.service.ftk;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import ru.rfsnab.integrationservice.config.IntegrationProperties;
import ru.rfsnab.integrationservice.dto.BatchImportRequest;
import ru.rfsnab.integrationservice.dto.BatchImportResponse;
import ru.rfsnab.integrationservice.dto.ProductImportItemDto;
import ru.rfsnab.integrationservice.dto.ProductImportItemDto.VariantImportItemDto;
import ru.rfsnab.integrationservice.model.ImportLog;
import ru.rfsnab.integrationservice.model.ImportLog.ImportStatus;
import ru.rfsnab.integrationservice.model.ftk.FtkProduct;
import ru.rfsnab.integrationservice.model.ftk.FtkProduct.FtkVariant;
import ru.rfsnab.integrationservice.repository.ImportLogRepository;
import ru.rfsnab.integrationservice.service.ftk.FtkFtpClient.FtpStreamHandle;
import ru.rfsnab.integrationservice.service.ftk.FtkXmlParser.ClassifierData;
import ru.rfsnab.integrationservice.service.ftk.FtkXmlParser.OfferData;
import ru.rfsnab.integrationservice.service.ftk.FtkXmlParser.ProductData;
import ru.rfsnab.integrationservice.service.ftk.FtkXmlParser.RestData;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Оркестратор импорта ФТК.
 *
 * importFromFtp() — актуальный метод: XML с FTP, выполняется асинхронно (@Async),
 *                    результат пишется в import_log (см. saveFtkLog).
 * importFromXls() — устаревший, сохранён для обратной совместимости, синхронный.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FtkImportService {

    public static final String EXCHANGE_TYPE_FTK = "FTK_CATALOG";

    private static final String SOURCE           = "FTK";
    private static final String BATCH_IMPORT_URI = "/api/v1/products/import/batch";

    private final FtkXlsParser xlsParser;
    private final FtkXmlParser xmlParser;
    private final FtkFtpClient ftpClient;
    private final FtkCategoryMapper categoryMapper;
    private final FtkImageDownloader imageDownloader;
    private final RestTemplate productServiceRestTemplate;
    private final IntegrationProperties properties;
    private final ImportLogRepository importLogRepository;

    private final AtomicBoolean importInProgress = new AtomicBoolean(false);

    // ══════════════════════════════════════════════════════════════
    // Актуальный метод: XML с FTP
    // ══════════════════════════════════════════════════════════════

    @Async("ftkImportExecutor")
    public void importFromFtp() {
        runImport(0);
    }

    /** Автовозобновление после рестарта сервиса (см. FtkImportResumer). */
    @Async("ftkImportExecutor")
    public void resumeImportFromFtp(int resumeAttempts) {
        runImport(resumeAttempts);
    }

    private void runImport(int resumeAttempts) {
        if (!importInProgress.compareAndSet(false, true)) {
            log.warn("ФТК XML импорт уже выполняется, повторный запуск пропущен");
            return;
        }
        try {
            doImportFromFtp(resumeAttempts);
        } catch (Exception e) {
            log.error("ФТК XML импорт (async) завершился с ошибкой: {}", e.getMessage(), e);
        } finally {
            importInProgress.set(false);
        }
    }

    // package-private — вызывается напрямую из юнит-тестов, минуя @Async-прокси
    FtkImportResult doImportFromFtp() throws Exception {
        return doImportFromFtp(0);
    }

    FtkImportResult doImportFromFtp(int resumeAttempts) throws Exception {
        LocalDateTime startedAt = LocalDateTime.now();
        // Запись IN_PROGRESS сразу: если JVM умрёт посреди импорта, по ней
        // FtkImportResumer при старте сервиса поймёт, что импорт надо доделать
        ImportLog logEntry = createInProgressLog(startedAt, resumeAttempts);
        IntegrationProperties.FtkProperties cfg = properties.getFtk();
        categoryMapper.resetCache();
        String rootDir = ftpClient.getRootDir();

        log.info("ФТК XML импорт запущен");

        try {
            // 1. Классификатор → группы + свойства + единицы измерения (общий для всех порций)
            String rootImportPath = ftpClient.findFileByPrefix(rootDir, "import___");
            if (rootImportPath == null) throw new IOException("Корневой import___.xml не найден на FTP");
            ClassifierData classifier;
            try (InputStream is = ftpClient.openStream(rootImportPath)) {
                classifier = xmlParser.parseClassifier(is);
            }
            categoryMapper.loadClassifier(classifier);

            // 2-6. Товары/офферы/цены/остатки/сборка — по каждой из трёх FTP-порций
            List<FtkProduct> allProducts = new ArrayList<>();
            for (int part = 1; part <= FtkFtpClient.GOODS_PARTS_COUNT; part++) {
                String goodsDir = ftpClient.getGoodsDir(part);
                try {
                    // 2. Товары
                    String goodsImportPath = ftpClient.findFileByPrefix(goodsDir, "import___");
                    if (goodsImportPath == null) {
                        log.warn("ФТК: goods/{}/import___.xml не найден на FTP, порция пропущена", part);
                        continue;
                    }
                    Map<String, ProductData> products;
                    try (InputStream is = ftpClient.openStream(goodsImportPath)) {
                        products = xmlParser.parseProducts(is, classifier);
                    }

                    // 3. Предложения
                    String offersPath = ftpClient.findFileByPrefix(goodsDir, "offers___");
                    if (offersPath == null) {
                        log.warn("ФТК: goods/{}/offers___.xml не найден на FTP, порция пропущена", part);
                        continue;
                    }
                    Map<String, OfferData> offers;
                    try (InputStream is = ftpClient.openStream(offersPath)) {
                        offers = xmlParser.parseOffers(is);
                    }

                    // 4. Цены — StAX стрим (125 МБ)
                    String pricesPath = ftpClient.findFileByPrefix(goodsDir, "prices___");
                    if (pricesPath == null) {
                        log.warn("ФТК: goods/{}/prices___.xml не найден на FTP, порция пропущена", part);
                        continue;
                    }
                    Map<String, BigDecimal> prices;
                    try (FtpStreamHandle handle = ftpClient.openLargeStream(pricesPath)) {
                        prices = xmlParser.parsePrices(handle.getStream());
                    }

                    // 5. Остатки
                    String restsPath = ftpClient.findFileByPrefix(goodsDir, "rests___");
                    if (restsPath == null) {
                        log.warn("ФТК: goods/{}/rests___.xml не найден на FTP, порция пропущена", part);
                        continue;
                    }
                    Map<String, RestData> rests;
                    try (InputStream is = ftpClient.openStream(restsPath)) {
                        rests = xmlParser.parseRests(is);
                    }

                    // 6. Сборка
                    List<FtkProduct> partProducts = xmlParser.assemble(products, offers, prices, rests, classifier, part);
                    log.info("ФТК: goods/{} — собрано {} товаров", part, partProducts.size());
                    allProducts.addAll(partProducts);
                } catch (Exception e) {
                    log.warn("ФТК: ошибка обработки порции goods/{} — порция пропущена: {}", part, e.getMessage());
                }
            }
            log.info("ФТК: всего собрано {} товаров из {} порций", allProducts.size(), FtkFtpClient.GOODS_PARTS_COUNT);

            // 7. Лимит
            int limit = cfg.getImportLimit();
            List<FtkProduct> limited = (limit > 0 && allProducts.size() > limit)
                    ? allProducts.subList(0, limit)
                    : allProducts;
            if (limit > 0 && allProducts.size() > limit) {
                log.info("ФТК: применён лимит {} из {}", limit, allProducts.size());
            }

            // 8. Маппинг и batch-импорт
            List<ProductImportItemDto> dtos = limited.stream()
                    .map(this::mapToDto)
                    .filter(dto -> dto != null)
                    .toList();

            BatchImportResult batchResult = sendBatch(dtos);
            log.info("ФТК batch-импорт: created={}, updated={}, unchanged={}, failed={}",
                    batchResult.created(), batchResult.updated(), batchResult.unchanged(), batchResult.failed());

            // 9. Изображения — все картинки каждого товара, кроме уже загруженных ранее
            List<String> externalIdsWithImages = limited.stream()
                    .filter(p -> !p.getImagePaths().isEmpty())
                    .map(p -> buildProductExternalId(p.getArticle()))
                    .toList();
            Map<String, Set<String>> existingFileKeysByProduct = imageDownloader.getExistingFileKeysBatch(externalIdsWithImages);

            int imagesOk = 0, imagesFailed = 0, imagesSkipped = 0;
            for (FtkProduct p : limited) {
                if (p.getImagePaths().isEmpty()) continue;
                String externalId = buildProductExternalId(p.getArticle());
                Set<String> existingFileKeys = existingFileKeysByProduct.getOrDefault(externalId, Set.of());
                for (String imagePath : p.getImagePaths()) {
                    String ftpImagePath = "ftp://" + properties.getFtk().getFtp().getHost()
                            + ftpClient.getGoodsDir(p.getPartNumber()) + imagePath;
                    String predictedFileKey = imageDownloader.predictFileKey(ftpImagePath, externalId);
                    if (existingFileKeys.contains(predictedFileKey)) {
                        imagesSkipped++;
                        log.debug("ФТК изображение уже загружено, пропуск: externalId={}, fileKey={}", externalId, predictedFileKey);
                        continue;
                    }
                    boolean ok = imageDownloader.downloadAndUpload(ftpImagePath, externalId);
                    if (ok) imagesOk++; else imagesFailed++;
                }
            }
            log.info("ФТК изображения: ok={}, failed={}, skipped={}", imagesOk, imagesFailed, imagesSkipped);

            FtkImportResult result = new FtkImportResult(limited.size(), batchResult.created(),
                    batchResult.updated(), batchResult.unchanged(), batchResult.failed(), imagesOk, imagesFailed, imagesSkipped);
            saveFtkLog(logEntry, startedAt, resumeAttempts, result, null);
            return result;

        } catch (Exception e) {
            log.error("ФТК XML импорт завершился с ошибкой: {}", e.getMessage(), e);
            saveFtkLog(logEntry, startedAt, resumeAttempts, null, e);
            throw e;
        }
    }

    // ══════════════════════════════════════════════════════════════
    // Устаревший метод: XLS
    // ══════════════════════════════════════════════════════════════

    @Deprecated
    public FtkImportResult importFromXls(InputStream xls) throws IOException {
        LocalDateTime startedAt = LocalDateTime.now();
        IntegrationProperties.FtkProperties cfg = properties.getFtk();
        categoryMapper.resetCache();

        try {
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
            log.info("ФТК XLS batch-импорт: created={}, updated={}, unchanged={}, failed={}",
                    batchResult.created(), batchResult.updated(), batchResult.unchanged(), batchResult.failed());

            int imagesOk = 0, imagesFailed = 0;
            for (FtkProduct p : products) {
                String externalId = buildProductExternalId(p.getArticle());
                for (String imagePath : p.getImagePaths()) {
                    boolean ok = imageDownloader.downloadAndUpload(imagePath, externalId);
                    if (ok) imagesOk++; else imagesFailed++;
                }
            }

            FtkImportResult result = new FtkImportResult(products.size(), batchResult.created(),
                    batchResult.updated(), batchResult.unchanged(), batchResult.failed(), imagesOk, imagesFailed, 0);
            saveFtkLog(null, startedAt, 0, result, null);
            return result;

        } catch (IOException e) {
            log.error("ФТК XLS импорт завершился с ошибкой: {}", e.getMessage(), e);
            saveFtkLog(null, startedAt, 0, null, e);
            throw e;
        }
    }

    // ══════════════════════════════════════════════════════════════
    // Маппинг
    // ══════════════════════════════════════════════════════════════

    private ProductImportItemDto mapToDto(FtkProduct p) {
        if (p == null || p.getArticle() == null || p.getName() == null) return null;

        String externalId = buildProductExternalId(p.getArticle());
        Long categoryId   = categoryMapper.resolveCategory(p.getGroupUuid());

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
                .wholesalePrice(basePrice)
                .vatRate(vatRate)
                .source(SOURCE)
                .categoryId(categoryId)
                .unitOfMeasure(p.getUnitOfMeasure())
                .imagePaths(p.getImagePaths())
                .properties(p.getProperties())
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
        return VariantImportItemDto.builder()
                .externalId(buildVariantExternalId(v.getArticle()))
                .sku(v.getArticle())
                .price(v.getPrice())
                .wholesalePrice(v.getPrice())
                .stockQuantity(Math.max(0, v.getStockQuantity()))
                .barcode(v.getBarcode())
                .countryOfOrigin(v.getCountryOfOrigin())
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
        if (items.isEmpty()) return new BatchImportResult(0, 0, 0, 0);

        int chunkSize = properties.getImportConfig().getChunkSize();
        int created = 0, updated = 0, unchanged = 0, failed = 0;

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
                    unchanged += body.getUnchanged();
                    failed  += body.getFailed();
                }
            } catch (Exception e) {
                log.error("Ошибка отправки chunk [{}-{}]: {}", i, i + chunk.size(), e.getMessage());
                failed += chunk.size();
            }
        }

        return new BatchImportResult(created, updated, unchanged, failed);
    }

    // ══════════════════════════════════════════════════════════════
    // Logging
    // ══════════════════════════════════════════════════════════════

    /**
     * Создаёт запись IN_PROGRESS в начале импорта. При ошибке БД импорт не блокируется —
     * финализация в saveFtkLog создаст запись с нуля (fallback на старое поведение).
     */
    private ImportLog createInProgressLog(LocalDateTime startedAt, int resumeAttempts) {
        try {
            return importLogRepository.save(ImportLog.builder()
                    .exchangeType(EXCHANGE_TYPE_FTK)
                    .status(ImportStatus.IN_PROGRESS)
                    .startedAt(startedAt)
                    .resumeAttempts(resumeAttempts)
                    .build());
        } catch (Exception e) {
            log.warn("Не удалось создать IN_PROGRESS запись import_log: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Финализирует запись импорта: обновляет IN_PROGRESS-запись или создаёт новую
     * (XLS-путь, fallback при ошибке createInProgressLog — resumeAttempts сохраняется и там,
     * чтобы не сбросить счётчик автовозобновлений).
     */
    private void saveFtkLog(ImportLog logEntry, LocalDateTime startedAt, int resumeAttempts,
                            FtkImportResult result, Exception error) {
        try {
            LocalDateTime completedAt = LocalDateTime.now();
            long durationMs = Duration.between(startedAt, completedAt).toMillis();

            ImportStatus status;
            if (error != null) {
                status = ImportStatus.FAILED;
            } else if (result == null) {
                status = ImportStatus.FAILED;
            } else if (result.failed() > 0 || result.imagesFailed() > 0) {
                status = ImportStatus.PARTIAL;
            } else {
                status = ImportStatus.SUCCESS;
            }

            if (logEntry == null) {
                logEntry = ImportLog.builder()
                        .exchangeType(EXCHANGE_TYPE_FTK)
                        .startedAt(startedAt)
                        .resumeAttempts(resumeAttempts)
                        .build();
            }
            logEntry.setStatus(status);
            logEntry.setTotalReceived(result != null ? result.totalProducts() : 0);
            logEntry.setCreated(result != null ? result.created() : 0);
            logEntry.setUpdated(result != null ? result.updated() : 0);
            logEntry.setUnchanged(result != null ? result.unchanged() : 0);
            logEntry.setFailed(result != null ? result.failed() : 0);
            logEntry.setImagesProcessed(result != null ? result.imagesOk() + result.imagesFailed() : 0);
            logEntry.setImagesFailed(result != null ? result.imagesFailed() : 0);
            logEntry.setDurationMs(durationMs);
            logEntry.setErrorMessage(error != null ? error.getMessage() : null);
            logEntry.setCompletedAt(completedAt);
            importLogRepository.save(logEntry);
        } catch (Exception ex) {
            log.error("Не удалось сохранить FTK import_log: {}", ex.getMessage());
        }
    }

    // ══════════════════════════════════════════════════════════════
    // Result types
    // ══════════════════════════════════════════════════════════════

    private record BatchImportResult(int created, int updated, int unchanged, int failed) {}

    public record FtkImportResult(
            int totalProducts,
            int created,
            int updated,
            int unchanged,
            int failed,
            int imagesOk,
            int imagesFailed,
            int imagesSkipped
    ) {}
}
