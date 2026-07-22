package ru.rfsnab.productservice.service;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.streaming.SXSSFSheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.rfsnab.productservice.dto.PriceListRequested;
import ru.rfsnab.productservice.dto.PriceListResponse;
import ru.rfsnab.productservice.exception.CategoryValidationException;
import ru.rfsnab.productservice.exception.PriceListDispatchException;
import ru.rfsnab.productservice.exception.PriceListNotFoundException;
import ru.rfsnab.productservice.exception.PriceListNotReadyException;
import ru.rfsnab.productservice.exception.PriceListPendingException;
import ru.rfsnab.productservice.model.PriceListRequest;
import ru.rfsnab.productservice.model.PriceListStatus;
import ru.rfsnab.productservice.model.Product;
import ru.rfsnab.productservice.repository.PriceListRequestRepository;
import ru.rfsnab.productservice.repository.ProductRepository;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Slf4j
@Service
@RequiredArgsConstructor
public class PriceListService {

    public static final String TOPIC_PRICE_LIST_REQUESTS = "price-list-requests";

    private static final long KAFKA_SEND_TIMEOUT_SECONDS = 5L;
    private static final int GENERATION_PAGE_SIZE = 1000;
    private static final int XLSX_ROW_ACCESS_WINDOW = 100;
    private static final int ERROR_MESSAGE_MAX_LENGTH = 1000;
    private static final String CLIENT_TYPE_B2B = "B2B";
    private static final String XLSX_CONTENT_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private static final int COL_WIDTH_SKU = 20 * 256;
    private static final int COL_WIDTH_NAME = 60 * 256;
    private static final int COL_WIDTH_UNIT = 12 * 256;
    private static final int COL_WIDTH_PRICE = 15 * 256;

    private final PriceListRequestRepository priceListRequestRepository;
    private final ProductRepository productRepository;
    private final CategoryService categoryService;
    private final StorageService storageService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final EntityManager entityManager;

    @Transactional
    public PriceListResponse create(Long userId, String clientType, List<Long> categoryIds) {
        for (Long categoryId : categoryIds) {
            if (!categoryService.existsById(categoryId)) {
                throw new CategoryValidationException("Категория с id=" + categoryId + " не найдена");
            }
        }

        if (priceListRequestRepository.existsByUserIdAndStatus(userId, PriceListStatus.PENDING)) {
            throw new PriceListPendingException("У вас уже формируется прайс, дождитесь готовности");
        }

        PriceListRequest request = PriceListRequest.builder()
                .userId(userId)
                .clientType(clientType)
                .categoryIds(categoryIds)
                .status(PriceListStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .build();

        try {
            request = priceListRequestRepository.saveAndFlush(request);
        } catch (DataIntegrityViolationException e) {
            // Гонка: второй PENDING-запрос успел проскочить быструю проверку existsByUserIdAndStatus
            throw new PriceListPendingException("У вас уже формируется прайс, дождитесь готовности");
        }

        try {
            kafkaTemplate.send(TOPIC_PRICE_LIST_REQUESTS, new PriceListRequested(request.getId()))
                    .get(KAFKA_SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            priceListRequestRepository.deleteById(request.getId());
            throw new PriceListDispatchException("Отправка события генерации прайс-листа прервана", e);
        } catch (ExecutionException | TimeoutException e) {
            priceListRequestRepository.deleteById(request.getId());
            throw new PriceListDispatchException("Не удалось отправить событие генерации прайс-листа", e);
        }

        return toResponse(request);
    }

    @Transactional(readOnly = true)
    public List<PriceListResponse> getMyRequests(Long userId) {
        return priceListRequestRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public ResponseInputStream<GetObjectResponse> download(Long userId, Long id) {
        PriceListRequest request = priceListRequestRepository.findById(id)
                .filter(r -> r.getUserId().equals(userId))
                .orElseThrow(() -> new PriceListNotFoundException(id));

        if (request.getStatus() != PriceListStatus.READY) {
            throw new PriceListNotReadyException("Прайс-лист ещё не готов, статус: " + request.getStatus());
        }

        if (request.getFileKey() == null || !storageService.fileExists(request.getFileKey())) {
            log.warn("Файл прайс-листа не найден в хранилище: requestId={}, fileKey={}", id, request.getFileKey());
            throw new PriceListNotFoundException(id);
        }

        return storageService.downloadStream(request.getFileKey());
    }

    @Transactional
    public void generate(Long requestId) {
        PriceListRequest request;
        try {
            request = priceListRequestRepository.findById(requestId).orElse(null);
        } catch (RuntimeException e) {
            log.error("Ошибка чтения запроса прайс-листа requestId={}: {}", requestId, e.getMessage(), e);
            return;
        }

        if (request == null) {
            log.warn("Прайс-лист requestId={} не найден (возможно, удалён при сбое отправки в Kafka), " +
                    "сообщение считаем обработанным", requestId);
            return;
        }

        if (request.getStatus() == PriceListStatus.READY) {
            log.info("Прайс-лист requestId={} уже готов, пропускаем повторную генерацию", requestId);
            return;
        }

        try (SXSSFWorkbook workbook = new SXSSFWorkbook(XLSX_ROW_ACCESS_WINDOW)) {
            List<Long> subtreeCategoryIds = categoryService.getSubtreeCategoryIds(request.getCategoryIds());
            SXSSFSheet sheet = workbook.createSheet("Прайс-лист");
            sheet.setColumnWidth(0, COL_WIDTH_SKU);
            sheet.setColumnWidth(1, COL_WIDTH_NAME);
            sheet.setColumnWidth(2, COL_WIDTH_UNIT);
            sheet.setColumnWidth(3, COL_WIDTH_PRICE);

            int rowIndex = 0;
            Row header = sheet.createRow(rowIndex++);
            writeCell(header, 0, "Артикул");
            writeCell(header, 1, "Наименование");
            writeCell(header, 2, "Ед.изм.");
            writeCell(header, 3, "Цена");

            boolean isB2B = CLIENT_TYPE_B2B.equals(request.getClientType());
            int rowCount = 0;
            int page = 0;
            Page<Product> productPage;
            do {
                productPage = productRepository.findByCategoryIdInAndIsActiveTrue(
                        subtreeCategoryIds, PageRequest.of(page, GENERATION_PAGE_SIZE));

                for (Product product : productPage.getContent()) {
                    Row row = sheet.createRow(rowIndex++);
                    BigDecimal price = isB2B ? product.getPrice() : product.getWholesalePrice();
                    writeCell(row, 0, product.getSku());
                    writeCell(row, 1, product.getName());
                    writeCell(row, 2, product.getUnitOfMeasure());
                    writeCell(row, 3, price != null ? price.toPlainString() : "");
                    rowCount++;
                }

                entityManager.clear();
                page++;
            } while (productPage.hasNext());

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            workbook.dispose();

            String fileKey = "price-lists/%d/%d.xlsx".formatted(request.getUserId(), request.getId());
            storageService.uploadBytes(out.toByteArray(), fileKey, XLSX_CONTENT_TYPE);

            // request откреплён от контекста entityManager.clear() в цикле — перечитываем управляемый объект
            PriceListRequest managed = priceListRequestRepository.findById(requestId).orElseThrow();
            managed.setStatus(PriceListStatus.READY);
            managed.setFileKey(fileKey);
            managed.setRowCount(rowCount);
            managed.setCompletedAt(LocalDateTime.now());
            priceListRequestRepository.save(managed);

            log.info("Прайс-лист requestId={} сгенерирован: rowCount={}, fileKey={}", requestId, rowCount, fileKey);
        } catch (IOException | RuntimeException e) {
            log.error("Ошибка генерации прайс-листа requestId={}: {}", requestId, e.getMessage(), e);
            markFailed(requestId, e.getMessage());
        }
    }

    private void markFailed(Long requestId, String errorMessage) {
        priceListRequestRepository.findById(requestId).ifPresent(request -> {
            request.setStatus(PriceListStatus.FAILED);
            request.setErrorMessage(truncate(errorMessage, ERROR_MESSAGE_MAX_LENGTH));
            request.setCompletedAt(LocalDateTime.now());
            priceListRequestRepository.save(request);
        });
    }

    private void writeCell(Row row, int column, String value) {
        Cell cell = row.createCell(column);
        cell.setCellValue(value != null ? value : "");
    }

    private String truncate(String message, int maxLength) {
        if (message == null) {
            return "Неизвестная ошибка";
        }
        return message.length() > maxLength ? message.substring(0, maxLength) : message;
    }

    private PriceListResponse toResponse(PriceListRequest request) {
        List<String> categoryNames = request.getCategoryIds().stream()
                .map(categoryService::getCategoryNameById)
                .filter(Objects::nonNull)
                .toList();

        return new PriceListResponse(
                request.getId(),
                request.getStatus().name(),
                categoryNames,
                request.getRowCount(),
                request.getCreatedAt(),
                request.getCompletedAt()
        );
    }
}
