package ru.rfsnab.integrationservice.service.catalog;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Unmarshaller;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;
import ru.rfsnab.integrationservice.config.IntegrationProperties;
import ru.rfsnab.integrationservice.dto.BatchImportRequest;
import ru.rfsnab.integrationservice.dto.BatchImportResponse;
import ru.rfsnab.integrationservice.dto.ImportResult;
import ru.rfsnab.integrationservice.dto.ProductImportItemDto;
import ru.rfsnab.integrationservice.model.ImportLog;
import ru.rfsnab.integrationservice.model.ImportLog.ImportStatus;
import ru.rfsnab.integrationservice.model.commerceml.*;
import ru.rfsnab.integrationservice.repository.ImportLogRepository;
import ru.rfsnab.integrationservice.service.ftk.FtkCategoryMapper;
import ru.rfsnab.integrationservice.service.ftk.FtkXmlParser.ClassifierData;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Основной pipeline импорта каталога из 1С через CommerceML.
 * Последовательность:
 * 1. Parse import.xml (товары) и offers.xml (цены + остатки)
 * 2. Merge данных по externalId
 * 3. Map в ProductImportItemDto
 * 4. Нарезка на chunk'и
 * 5. Параллельная отправка в product-service через virtual threads + Semaphore
 * 6. Постановка задач на обработку изображений
 * 7. Логирование результата в import_log
 * Virtual threads (Java 21) выбраны потому что задача I/O-bound:
 * каждый chunk — это HTTP-вызов с ожиданием ответа. Virtual threads
 * не блокируют platform threads при ожидании, позволяя эффективно
 * параллелить десятки HTTP-запросов без пула фиксированного размера.
 * Semaphore ограничивает concurrency, чтобы не перегрузить product-service.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CatalogImportService {

    private static final String IMPORT_XML = "import.xml";
    private static final String OFFERS_XML = "offers.xml";
    private static final String BATCH_IMPORT_URI = "/api/v1/products/import/batch";

    private static final String CATALOG_ROOT_SLUG = "import-1c";

    private final JAXBContext commerceMlJaxbContext;
    private final RestTemplate productServiceRestTemplate;
    private final IntegrationProperties properties;
    private final ImportLogRepository importLogRepository;
    private final ImageProcessingPool imageProcessingPool;
    private final FtkCategoryMapper categoryMapper;

    /**
     * Запускает полный цикл импорта каталога.
     * Вызывается из CommerceMLExchangeController при type=catalog, mode=import.
     *
     * @param exchangeDir директория обмена с файлами import.xml и offers.xml
     * @param sessionId   ID текущей сессии обмена
     * @return "success" или "failure\n{причина}" (протокол CommerceML)
     */
    public String processImport(Path exchangeDir, String sessionId) {
        LocalDateTime startedAt = LocalDateTime.now();
        log.info("Начало импорта каталога. Session: {}, dir: {}", sessionId, exchangeDir);

        try {
            // 1. Parse XML файлы
            logReceivedFiles(exchangeDir, sessionId);
            CommerceInfo importInfo = parseXmlFile(exchangeDir.resolve(IMPORT_XML));
            CommerceInfo offersInfo = parseOffersIfExists(exchangeDir.resolve(OFFERS_XML));

            List<CmlProduct> products = extractProducts(importInfo);
            if (products.isEmpty()) {
                log.warn("import.xml не содержит товаров. Session: {}", sessionId);
                saveImportLog(sessionId, "CATALOG", ImportStatus.SUCCESS,
                        0, 0, 0, 0, null, startedAt);
                return "success";
            }

            // 1.5. Построить дерево категорий из Классификатора
            categoryMapper.resetCache();
            ClassifierData classifierData = buildClassifierData(importInfo.getClassifier());
            categoryMapper.loadClassifier(classifierData, CATALOG_ROOT_SLUG);
            log.info("Загружен классификатор 1С: {} групп. Session: {}",
                    classifierData.groupPaths().size(), sessionId);

            // 2. Merge: товары + цены/остатки по externalId
            Map<String, Offer> offersById = indexOffers(offersInfo);
            List<PriceType> priceTypes = (offersInfo != null && offersInfo.getOffersPackage() != null)
                    ? offersInfo.getOffersPackage().getPriceTypes()
                    : Collections.emptyList();
            List<ProductImportItemDto> importItems = mergeAndMap(products, offersById, priceTypes);
            log.info("Подготовлено {} товаров для импорта. Session: {}", importItems.size(), sessionId);

            // 3. Chunk + parallel send
            int chunkSize = properties.getImportConfig().getChunkSize();
            List<List<ProductImportItemDto>> chunks = partition(importItems, chunkSize);
            log.info("Разбито на {} chunk(ов) по {}. Session: {}", chunks.size(), chunkSize, sessionId);

            ImportResult result = sendChunksInParallel(chunks);
            categoryMapper.resetCache();

            // 4. Постановка задач на обработку изображений
            enqueueImageTasks(exchangeDir, products, sessionId);

            // 5. Log
            ImportStatus status = resolveStatus(result);
            saveImportLog(sessionId, "CATALOG", status, result.totalItems(),
                    result.createdCount(), result.updatedCount(), result.failedCount(),
                    result.errors().isEmpty() ? null : String.join("; ", result.errors()),
                    startedAt);

            log.info("Импорт завершён. Status: {}, created: {}, updated: {}, failed: {}. Session: {}",
                    status, result.createdCount(), result.updatedCount(), result.failedCount(), sessionId);

            return status == ImportStatus.FAILED
                    ? "failure\nВсе chunk'и завершились с ошибкой"
                    : "success";

        } catch (Exception e) {
            log.error("Критическая ошибка импорта каталога. Session: {}", sessionId, e);
            saveImportLog(sessionId, "CATALOG", ImportStatus.FAILED,
                    0, 0, 0, 0, e.getMessage(), startedAt);
            return "failure\n" + e.getMessage();
        }
    }

    // ==================== XML Parsing ====================

    private void logReceivedFiles(Path exchangeDir, String sessionId) {
        try {
            Path importXml = exchangeDir.resolve(IMPORT_XML);
            Path offersXml = exchangeDir.resolve(OFFERS_XML);

            boolean hasImport = Files.exists(importXml);
            boolean hasOffers = Files.exists(offersXml);

            log.info("Файлы обмена. Session: {}. import.xml: {} ({}), offers.xml: {} ({})",
                    sessionId,
                    hasImport ? "есть" : "ОТСУТСТВУЕТ",
                    hasImport ? Files.size(importXml) + " байт" : "-",
                    hasOffers ? "есть" : "ОТСУТСТВУЕТ",
                    hasOffers ? Files.size(offersXml) + " байт" : "-");

            if (!hasOffers) {
                log.warn("offers.xml не получен от 1С — цены и остатки обновлены не будут. Session: {}", sessionId);
            }

            // Лог картинок (import_files/)
            Path imagesDir = exchangeDir.resolve("import_files");
            if (Files.exists(imagesDir)) {
                try (var stream = Files.walk(imagesDir)) {
                    long imageCount = stream.filter(Files::isRegularFile).count();
                    log.info("Получено файлов изображений: {}. Session: {}", imageCount, sessionId);
                }
            }
        } catch (IOException e) {
            log.warn("Не удалось прочитать список файлов обмена. Session: {}", sessionId, e);
        }
    }

    /**
     * JAXB unmarshalling XML-файла в CommerceInfo.
     * Unmarshaller создаётся per-call — он не thread-safe, но дешёвый в создании.
     */
    private CommerceInfo parseXmlFile(Path xmlFile) {
        log.debug("Парсинг XML: {}", xmlFile);
        if (!Files.exists(xmlFile)) {
            throw new IllegalStateException("XML-файл не найден: " + xmlFile.getFileName());
        }
        try {
            Unmarshaller unmarshaller = commerceMlJaxbContext.createUnmarshaller();
            return (CommerceInfo) unmarshaller.unmarshal(xmlFile.toFile());
        } catch (JAXBException e) {
            throw new IllegalStateException("Ошибка парсинга XML: " + xmlFile.getFileName(), e);
        }
    }

    /**
     * offers.xml может отсутствовать (если 1С выгружает только каталог без цен).
     * В этом случае товары импортируются без цены — она обновится при следующей выгрузке.
     */
    private CommerceInfo parseOffersIfExists(Path offersFile) {
        if (Files.exists(offersFile)) {
            return parseXmlFile(offersFile);
        }
        log.info("offers.xml отсутствует — импорт без цен и остатков");
        return null;
    }

    private List<CmlProduct> extractProducts(CommerceInfo importInfo) {
        if (importInfo.getCatalog() == null || importInfo.getCatalog().getProducts() == null) {
            return Collections.emptyList();
        }
        return importInfo.getCatalog().getProducts();
    }

    // ==================== Merge & Mapping ====================

    /**
     * Индексирует предложения (Offer) из offers.xml по ID для быстрого lookup.
     */
    private Map<String, Offer> indexOffers(CommerceInfo offersInfo) {
        if (offersInfo == null || offersInfo.getOffersPackage() == null
                || offersInfo.getOffersPackage().getOffers() == null) {
            return Collections.emptyMap();
        }
        return offersInfo.getOffersPackage().getOffers().stream()
                .filter(o -> o.getId() != null)
                .collect(Collectors.toMap(Offer::getId, Function.identity(), (a, b) -> a));
    }

    /**
     * Объединяет данные из import.xml (товар) и offers.xml (цена/остаток)
     * по совпадению externalId, маппит в DTO для product-service.
     */
    private List<ProductImportItemDto> mergeAndMap(List<CmlProduct> products,
                                                   Map<String, Offer> offersById,
                                                   List<PriceType> priceTypes) {
        return products.stream()
                .map(product -> mapToImportItem(product, offersById.get(product.getId()), priceTypes, categoryMapper))
                .filter(Objects::nonNull)
                .toList();
    }

    /**
     * Маппинг CmlProduct + Offer → ProductImportItemDto.
     * Числовые поля парсятся здесь, чтобы JAXB-слой не падал на невалидных данных из 1С.
     * price = "Оптовая" (B2B цена), wholesalePrice = "Розничная" (B2C цена).
     */
    private ProductImportItemDto mapToImportItem(CmlProduct product, Offer offer,
                                                  List<PriceType> priceTypes, FtkCategoryMapper mapper) {
        if (product.getId() == null || product.getName() == null) {
            log.warn("Пропущен товар без Ид или Наименования: {}", product.getId());
            return null;
        }

        String firstGroupId = (product.getGroupIds() != null && !product.getGroupIds().isEmpty())
                ? product.getGroupIds().get(0) : null;
        Long categoryId = mapper.resolveCategory(firstGroupId);

        ProductImportItemDto.ProductImportItemDtoBuilder builder = ProductImportItemDto.builder()
                .externalId(product.getId())
                .name(product.getName().trim())
                .sku(product.getSku())
                .shortDescription(truncate(product.getDescription(), 1000))
                .unitOfMeasure(extractUnitOfMeasure(product))
                .categoryId(categoryId);

        if (offer != null) {
            builder.price(extractPriceByType(offer, priceTypes, "Оптовая цена"))
                    .wholesalePrice(extractPriceByType(offer, priceTypes, "Розничная цена"))
                    .stockQuantity(parseStock(offer.getQuantity()))
                    .vatRate(extractVatRate(offer));
        }

        return builder.build();
    }

    /**
     * Строит ClassifierData из JAXB Classifier (из import.xml 1С).
     * Рекурсивно обходит дерево групп, формируя groupPaths и groupParents.
     */
    private ClassifierData buildClassifierData(Classifier classifier) {
        Map<String, String> groupPaths   = new LinkedHashMap<>();
        Map<String, String> groupParents = new HashMap<>();
        if (classifier != null && classifier.getGroups() != null) {
            walkGroups(classifier.getGroups(), null, "", groupPaths, groupParents);
        }
        return new ClassifierData(groupPaths, groupParents, Map.of(), Map.of());
    }

    private void walkGroups(List<Group> groups, String parentUuid, String parentPath,
                             Map<String, String> paths, Map<String, String> parents) {
        for (Group group : groups) {
            if (group.getId() == null || group.getId().isBlank()) continue;
            String path = parentPath.isBlank() ? group.getName() : parentPath + " > " + group.getName();
            paths.put(group.getId(), path);
            parents.put(group.getId(), parentUuid);
            if (group.getSubGroups() != null && !group.getSubGroups().isEmpty()) {
                walkGroups(group.getSubGroups(), group.getId(), path, paths, parents);
            }
        }
    }

    private String extractUnitOfMeasure(CmlProduct product) {
        if (product.getBaseUnit() == null) {
            return null;
        }
        String value = product.getBaseUnit().getValue();
        return (value != null && !value.isBlank()) ? value.trim() : product.getBaseUnit().getFullName();
    }

    private BigDecimal extractPriceByType(Offer offer, List<PriceType> priceTypes, String typeName) {
        if (offer.getPrices() == null || offer.getPrices().isEmpty()) return null;
        String targetTypeId = priceTypes.stream()
                .filter(pt -> pt.getName() != null && pt.getName().toLowerCase().contains(typeName.toLowerCase()))
                .map(PriceType::getId)
                .findFirst()
                .orElse(null);
        if (targetTypeId == null) return null;
        return offer.getPrices().stream()
                .filter(p -> targetTypeId.equals(p.getPriceTypeId()))
                .findFirst()
                .map(p -> parseBigDecimal(p.getPricePerUnit(), "цена " + typeName))
                .orElse(null);
    }

    private Integer extractVatRate(Offer offer) {
        if (offer.getTaxRates() == null || offer.getTaxRates().isEmpty()) {
            return null;
        }
        TaxRate taxRate = offer.getTaxRates().getFirst();
        String rate = taxRate.getRate();
        if (rate == null || "Без НДС".equalsIgnoreCase(rate.trim())) {
            return 0;
        }
        BigDecimal bd = parseBigDecimal(rate, "НДС");
        return bd != null ? bd.intValue() : null;
    }

    private BigDecimal parseBigDecimal(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(value.trim().replace(",", "."));
        } catch (NumberFormatException e) {
            log.warn("Невалидное числовое значение для {}: '{}'", fieldName, value);
            return null;
        }
    }

    private Integer parseStock(String quantity) {
        if (quantity == null || quantity.isBlank()) {
            return null;
        }
        try {
            // 1С может прислать "10.000" для целого количества
            return new BigDecimal(quantity.trim().replace(",", ".")).intValue();
        } catch (NumberFormatException e) {
            log.warn("Невалидное количество: '{}'", quantity);
            return null;
        }
    }

    // ==================== Chunking & Parallel Send ====================

    /**
     * Параллельная отправка chunk'ов в product-service.
     * Virtual threads + Semaphore: каждый chunk в отдельном virtual thread,
     * Semaphore ограничивает количество одновременных запросов.
     */
    private ImportResult sendChunksInParallel(List<List<ProductImportItemDto>> chunks) {
        int maxConcurrent = properties.getImportConfig().getMaxConcurrentRequests();
        Semaphore semaphore = new Semaphore(maxConcurrent);

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<CompletableFuture<BatchImportResponse>> futures = chunks.stream()
                    .map(chunk -> CompletableFuture.supplyAsync(() -> {
                        try {
                            semaphore.acquire();
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            throw new RuntimeException("Прервано ожидание семафора", e);
                        }
                        try {
                            return sendChunk(chunk);
                        } finally {
                            semaphore.release();
                        }
                    }, executor))
                    .toList();

            return aggregateResults(futures);
        }
    }

    /**
     * Отправляет один chunk в product-service через WebClient.
     * block() внутри virtual thread — ок: не блокирует platform thread.
     */
    private BatchImportResponse sendChunk(List<ProductImportItemDto> chunk) {
        BatchImportRequest request = new BatchImportRequest(chunk);
        String url = properties.getProductService().getUrl() + BATCH_IMPORT_URI;
        try {
            ResponseEntity<BatchImportResponse> response = productServiceRestTemplate.postForEntity(
                    url, request, BatchImportResponse.class
            );
            BatchImportResponse body = response.getBody();
            if (body == null) {
                return errorResponse(chunk.size(), "Пустой ответ от product-service");
            }
            log.debug("Chunk отправлен: created={}, updated={}, failed={}",
                    body.getCreated(), body.getUpdated(), body.getFailed());
            return body;
        } catch (HttpClientErrorException | HttpServerErrorException e) {
            log.error("HTTP ошибка при отправке chunk: {} {}", e.getStatusCode(), e.getResponseBodyAsString());
            return errorResponse(chunk.size(), "HTTP " + e.getStatusCode() + ": " + e.getResponseBodyAsString());
        } catch (Exception e) {
            log.error("Ошибка при отправке chunk в product-service", e);
            return errorResponse(chunk.size(), e.getMessage());
        }
    }

    private BatchImportResponse errorResponse(int chunkSize, String errorMessage) {
        BatchImportResponse response = new BatchImportResponse();
        response.setTotalReceived(chunkSize);
        response.setFailed(chunkSize);
        response.setErrors(List.of(errorMessage));
        return response;
    }

    private ImportResult aggregateResults(List<CompletableFuture<BatchImportResponse>> futures) {
        int totalItems = 0;
        int createdCount = 0;
        int updatedCount = 0;
        int failedCount = 0;
        List<String> allErrors = new ArrayList<>();

        for (CompletableFuture<BatchImportResponse> future : futures) {
            try {
                BatchImportResponse response = future.join();
                totalItems += response.getTotalReceived();
                createdCount += response.getCreated();
                updatedCount += response.getUpdated();
                failedCount += response.getFailed();
                if (response.getErrors() != null) {
                    allErrors.addAll(response.getErrors());
                }
            } catch (Exception e) {
                log.error("Необработанная ошибка в CompletableFuture", e);
                allErrors.add("Ошибка future: " + e.getMessage());
            }
        }

        return new ImportResult(totalItems, createdCount, updatedCount, failedCount, allErrors);
    }

    // ==================== Image Tasks ====================

    /**
     * Создаёт задачи на обработку изображений для товаров с тегом Картинка.
     * Задачи попадают в БД (image_processing_tasks) и подхватываются ImageProcessingPool.
     * Вызывается ПОСЛЕ успешного импорта товаров — картинки привязываются
     * к уже существующим товарам в product-service.
     */
    private void enqueueImageTasks(Path exchangeDir, List<CmlProduct> products, String sessionId) {
        int enqueued = 0;
        for (CmlProduct product : products) {
            if (product.getImages() != null && !product.getImages().isEmpty()) {
                imageProcessingPool.enqueueImages(
                        exchangeDir, product.getId(), product.getImages(), sessionId);
                enqueued += product.getImages().size();
            }
        }
        if (enqueued > 0) {
            log.info("Создано {} задач на обработку изображений. Session: {}", enqueued, sessionId);
        }
    }

    // ==================== Utilities ====================

    private ImportStatus resolveStatus(ImportResult result) {
        if (result.isFullySuccessful()) return ImportStatus.SUCCESS;
        if (result.isPartiallySuccessful()) return ImportStatus.PARTIAL;
        return ImportStatus.FAILED;
    }

    private void saveImportLog(String sessionId, String exchangeType, ImportStatus status,
                               int totalItems, int createdCount, int updatedCount,
                               int failedCount, String errorMessage,
                               LocalDateTime startedAt) {
        try {
            LocalDateTime completedAt = LocalDateTime.now();
            long durationMs = Duration.between(startedAt, completedAt).toMillis();

            ImportLog logEntry = ImportLog.builder()
                    .sessionId(sessionId)
                    .exchangeType(exchangeType)
                    .status(status)
                    .totalReceived(totalItems)
                    .created(createdCount)
                    .updated(updatedCount)
                    .failed(failedCount)
                    .durationMs(durationMs)
                    .errorMessage(errorMessage)
                    .startedAt(startedAt)
                    .completedAt(completedAt)
                    .build();
            importLogRepository.save(logEntry);
        } catch (Exception e) {
            // Ошибка записи лога не должна ломать основной процесс
            log.error("Не удалось сохранить import_log. Session: {}", sessionId, e);
        }
    }

    /**
     * Разбивает список на подсписки фиксированного размера.
     */
    private <T> List<List<T>> partition(List<T> list, int chunkSize) {
        List<List<T>> chunks = new ArrayList<>();
        for (int i = 0; i < list.size(); i += chunkSize) {
            chunks.add(list.subList(i, Math.min(i + chunkSize, list.size())));
        }
        return chunks;
    }

    /**
     * Метод обрезки краткого описания до 1000 символов
     */
    private String truncate(String value, int maxLength) {
        if (value == null) return null;
        return value.length() > maxLength ? value.substring(0, maxLength) : value;
    }
}