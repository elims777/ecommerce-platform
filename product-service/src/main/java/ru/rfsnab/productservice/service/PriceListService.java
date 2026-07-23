package ru.rfsnab.productservice.service;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.ClientAnchor;
import org.apache.poi.ss.usermodel.CreationHelper;
import org.apache.poi.ss.usermodel.Drawing;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.streaming.SXSSFSheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.rfsnab.productservice.dto.CategoryTreeDTO;
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
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
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

    private static final int COL_NUMBER = 0;
    private static final int COL_SKU = 1;
    private static final int COL_NAME = 2;
    private static final int COL_UNIT = 3;
    private static final int COL_PRICE = 4;
    private static final int TABLE_COLUMN_COUNT = 5;

    private static final int COL_WIDTH_NUMBER = 6 * 256;
    private static final int COL_WIDTH_SKU = 20 * 256;
    private static final int COL_WIDTH_NAME = 69 * 256;              // +15% к прежним 60
    private static final int COL_WIDTH_UNIT = (int) (12 * 0.8 * 256); // -20% к прежним 12
    private static final int COL_WIDTH_PRICE = 15 * 256;

    private static final String FONT_NAME = "Arial";
    private static final short FONT_SIZE = 11;
    private static final byte[] COLOR_CATEGORY_BG = new byte[]{(byte) 0xD9, (byte) 0xE2, (byte) 0xEC};

    private static final byte[] COLOR_NAVY = new byte[]{(byte) 0x1E, (byte) 0x3A, (byte) 0x5F};
    private static final byte[] COLOR_RED = new byte[]{(byte) 0xC0, (byte) 0x27, (byte) 0x2D};

    private static final String LOGO_CLASSPATH = "pricelist/logo.png";
    private static final int LOGO_ANCHOR_ROW_FROM = 0;
    private static final int LOGO_ANCHOR_ROW_TO = 4;
    private static final int LOGO_ANCHOR_COL_FROM = 0;
    private static final int LOGO_ANCHOR_COL_TO = 2;   // exclusive: занимает колонки A и B
    private static final int HEADER_BAND_ROWS = 4;
    private static final float HEADER_ROW_HEIGHT_POINTS = 24f;

    private static final String COMPANY_NAME = "ООО «МСВ»";
    private static final String COMPANY_PHONE = "Тел.: 8-800-201-78-01, 8-8212-29-69-71";
    private static final String COMPANY_EMAIL_SITE = "Email: msvkomi@mail.ru   Сайт: rfsnab.ru";
    private static final String COMPANY_ADDRESS =
            "Адрес: 167000, Республика Коми, г. Сыктывкар, Сысольское шоссе, д. 69, офис 12";

    private static final String PRICE_ON_REQUEST = "По запросу";

    private static final DateTimeFormatter PRICE_LIST_DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy");

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
            SXSSFSheet sheet = workbook.createSheet("Прайс-лист");
            sheet.setColumnWidth(COL_NUMBER, COL_WIDTH_NUMBER);
            sheet.setColumnWidth(COL_SKU, COL_WIDTH_SKU);
            sheet.setColumnWidth(COL_NAME, COL_WIDTH_NAME);
            sheet.setColumnWidth(COL_UNIT, COL_WIDTH_UNIT);
            sheet.setColumnWidth(COL_PRICE, COL_WIDTH_PRICE);

            Map<String, CellStyle> styles = createStyles(workbook);

            int rowIndex = writeSheetHeader(workbook, sheet, styles);
            rowIndex = writeTableHeader(sheet, styles, rowIndex);
            sheet.createFreezePane(0, rowIndex);

            boolean isB2B = CLIENT_TYPE_B2B.equals(request.getClientType());
            List<CategoryTreeDTO> selectedNodes = findSelectedCategoryNodes(request.getCategoryIds());

            // rowIndexHolder[0] = текущая строка листа, rowIndexHolder[1] = сквозной номер товара (rowCount)
            int[] rowIndexHolder = new int[]{rowIndex, 0};
            Set<Long> visitedCategoryIds = new LinkedHashSet<>();
            Map<Integer, CellStyle> categoryStylesByLevel = new HashMap<>();
            for (CategoryTreeDTO node : selectedNodes) {
                writeCategoryBranch(sheet, styles, node, 0, isB2B, rowIndexHolder, visitedCategoryIds,
                        categoryStylesByLevel);
            }
            int rowCount = rowIndexHolder[1];

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

    private void writeCell(Row row, int column, String value, CellStyle style) {
        Cell cell = row.createCell(column);
        cell.setCellValue(value != null ? value : "");
        cell.setCellStyle(style);
    }

    /**
     * Создаёт все стили и шрифты один раз до генерации строк (лимит POI ~64k стилей на книгу).
     */
    private Map<String, CellStyle> createStyles(SXSSFWorkbook workbook) {
        Font whiteBoldFont = workbook.createFont();
        whiteBoldFont.setFontName(FONT_NAME);
        whiteBoldFont.setFontHeightInPoints(FONT_SIZE);
        whiteBoldFont.setBold(true);
        whiteBoldFont.setColor(IndexedColors.WHITE.getIndex());

        Font whiteFont = workbook.createFont();
        whiteFont.setFontName(FONT_NAME);
        whiteFont.setFontHeightInPoints(FONT_SIZE);
        whiteFont.setColor(IndexedColors.WHITE.getIndex());

        XSSFFont redBoldFont = (XSSFFont) workbook.createFont();
        redBoldFont.setFontName(FONT_NAME);
        redBoldFont.setFontHeightInPoints(FONT_SIZE);
        redBoldFont.setBold(true);
        redBoldFont.setColor(new XSSFColor(COLOR_RED, null));

        Font boldFont = workbook.createFont();
        boldFont.setFontName(FONT_NAME);
        boldFont.setFontHeightInPoints(FONT_SIZE);
        boldFont.setBold(true);

        Font plainFont = workbook.createFont();
        plainFont.setFontName(FONT_NAME);
        plainFont.setFontHeightInPoints(FONT_SIZE);

        XSSFCellStyle headerBandStyle = (XSSFCellStyle) workbook.createCellStyle();
        headerBandStyle.setFillForegroundColor(new XSSFColor(COLOR_NAVY, null));
        headerBandStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        headerBandStyle.setFont(whiteBoldFont);

        XSSFCellStyle contactStyle = (XSSFCellStyle) workbook.createCellStyle();
        contactStyle.setFillForegroundColor(new XSSFColor(COLOR_NAVY, null));
        contactStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        contactStyle.setFont(whiteFont);
        contactStyle.setAlignment(HorizontalAlignment.RIGHT);

        XSSFCellStyle accentStyle = (XSSFCellStyle) workbook.createCellStyle();
        accentStyle.setFont(redBoldFont);

        XSSFCellStyle tableHeaderStyle = (XSSFCellStyle) workbook.createCellStyle();
        tableHeaderStyle.setFillForegroundColor(new XSSFColor(COLOR_NAVY, null));
        tableHeaderStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        tableHeaderStyle.setFont(whiteBoldFont);
        setThinBorders(tableHeaderStyle);

        XSSFCellStyle categoryStyle = (XSSFCellStyle) workbook.createCellStyle();
        categoryStyle.setFont(boldFont);
        categoryStyle.setWrapText(true);
        categoryStyle.setFillForegroundColor(new XSSFColor(COLOR_CATEGORY_BG, null));
        categoryStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        categoryStyle.setBorderTop(BorderStyle.MEDIUM);
        categoryStyle.setBorderBottom(BorderStyle.MEDIUM);

        XSSFCellStyle productNameStyle = (XSSFCellStyle) workbook.createCellStyle();
        productNameStyle.setFont(plainFont);
        productNameStyle.setWrapText(true);

        XSSFCellStyle productCellStyle = (XSSFCellStyle) workbook.createCellStyle();
        productCellStyle.setFont(plainFont);

        XSSFCellStyle priceStyle = (XSSFCellStyle) workbook.createCellStyle();
        priceStyle.setFont(plainFont);
        priceStyle.setAlignment(HorizontalAlignment.RIGHT);

        XSSFCellStyle unitStyle = (XSSFCellStyle) workbook.createCellStyle();
        unitStyle.setFont(plainFont);
        unitStyle.setAlignment(HorizontalAlignment.CENTER);

        XSSFCellStyle numberStyle = (XSSFCellStyle) workbook.createCellStyle();
        numberStyle.setFont(plainFont);
        numberStyle.setAlignment(HorizontalAlignment.CENTER);

        Map<String, CellStyle> styles = new LinkedHashMap<>();
        styles.put("headerBand", headerBandStyle);
        styles.put("contact", contactStyle);
        styles.put("accent", accentStyle);
        styles.put("tableHeader", tableHeaderStyle);
        styles.put("category", categoryStyle);
        styles.put("productName", productNameStyle);
        styles.put("productCell", productCellStyle);
        styles.put("price", priceStyle);
        styles.put("unit", unitStyle);
        styles.put("number", numberStyle);
        return styles;
    }

    private void setThinBorders(XSSFCellStyle style) {
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
    }

    /**
     * Пишет фирменную шапку листа (navy-полоса, логотип, контакты, строка "Прайс-лист от dd.MM.yyyy").
     * Возвращает индекс следующей свободной строки.
     */
    private int writeSheetHeader(SXSSFWorkbook workbook, SXSSFSheet sheet, Map<String, CellStyle> styles) {
        for (int i = 0; i < HEADER_BAND_ROWS; i++) {
            Row row = sheet.createRow(i);
            row.setHeightInPoints(HEADER_ROW_HEIGHT_POINTS);
            for (int col = 0; col < TABLE_COLUMN_COUNT; col++) {
                Cell cell = row.createCell(col);
                cell.setCellStyle(styles.get("headerBand"));
            }
        }

        Row contactRow0 = sheet.getRow(0);
        writeCell(contactRow0, COL_UNIT, COMPANY_NAME, styles.get("contact"));
        Row contactRow1 = sheet.getRow(1);
        writeCell(contactRow1, COL_UNIT, COMPANY_PHONE, styles.get("contact"));
        Row contactRow2 = sheet.getRow(2);
        writeCell(contactRow2, COL_UNIT, COMPANY_EMAIL_SITE, styles.get("contact"));
        Row contactRow3 = sheet.getRow(3);
        writeCell(contactRow3, COL_UNIT, COMPANY_ADDRESS, styles.get("contact"));

        // Объединяем ячейки под логотип: A1:B4 (строки 0-3 включ., колонки A-B / 0-1 включ.)
        sheet.addMergedRegion(new CellRangeAddress(0, 3, 0, 1));

        insertLogo(workbook, sheet);

        int accentRowIndex = HEADER_BAND_ROWS;
        Row accentRow = sheet.createRow(accentRowIndex);
        String accentText = "Прайс-лист от " + LocalDate.now().format(PRICE_LIST_DATE_FORMAT);
        writeCell(accentRow, COL_NUMBER, accentText, styles.get("accent"));

        return accentRowIndex + 1;
    }

    /**
     * Вставляет логотип из classpath в левый верхний угол шапки. Если ресурс недоступен — пропускает без падения.
     */
    private void insertLogo(SXSSFWorkbook workbook, SXSSFSheet sheet) {
        try (InputStream logoStream = new ClassPathResource(LOGO_CLASSPATH).getInputStream()) {
            byte[] logoBytes = logoStream.readAllBytes();
            int pictureIndex = workbook.addPicture(logoBytes, Workbook.PICTURE_TYPE_PNG);

            CreationHelper helper = workbook.getCreationHelper();
            Drawing<?> drawing = sheet.createDrawingPatriarch();
            ClientAnchor anchor = helper.createClientAnchor();
            anchor.setCol1(LOGO_ANCHOR_COL_FROM);
            anchor.setRow1(LOGO_ANCHOR_ROW_FROM);
            anchor.setCol2(LOGO_ANCHOR_COL_TO);
            anchor.setRow2(LOGO_ANCHOR_ROW_TO);
            drawing.createPicture(anchor, pictureIndex);
        } catch (IOException | RuntimeException e) {
            log.warn("Не удалось вставить логотип в прайс-лист: {}", e.getMessage());
        }
    }

    /**
     * Пишет строку заголовков таблицы (№, Артикул, Наименование, Ед.изм., Цена) и возвращает индекс следующей строки.
     */
    private int writeTableHeader(SXSSFSheet sheet, Map<String, CellStyle> styles, int rowIndex) {
        Row header = sheet.createRow(rowIndex);
        CellStyle style = styles.get("tableHeader");
        writeCell(header, COL_NUMBER, "№", style);
        writeCell(header, COL_SKU, "Артикул", style);
        writeCell(header, COL_NAME, "Наименование", style);
        writeCell(header, COL_UNIT, "Ед.изм.", style);
        writeCell(header, COL_PRICE, "Цена (руб.)", style);
        return rowIndex + 1;
    }

    /**
     * Находит узлы дерева категорий, соответствующие выбранным id, и оставляет только верхнеуровневые
     * (без предка среди других выбранных) — иначе потомок, идущий в списке раньше родителя, вывелся бы
     * отдельной top-level веткой без отступа и без учёта позиции в дереве. Результат отсортирован
     * по displayOrder для детерминированного порядка, совпадающего с деревом сайта.
     */
    private List<CategoryTreeDTO> findSelectedCategoryNodes(List<Long> categoryIds) {
        List<CategoryTreeDTO> tree = categoryService.getCategoryTree();
        List<CategoryTreeDTO> nodes = new ArrayList<>();
        for (Long categoryId : categoryIds) {
            CategoryTreeDTO node = findNode(tree, categoryId);
            if (node != null) {
                nodes.add(node);
            }
        }

        List<CategoryTreeDTO> topLevel = new ArrayList<>();
        for (CategoryTreeDTO node : nodes) {
            boolean hasSelectedAncestor = nodes.stream()
                    .anyMatch(other -> other != node && findNode(other.getChildren(), node.getId()) != null);
            if (!hasSelectedAncestor) {
                topLevel.add(node);
            }
        }

        topLevel.sort(Comparator.comparing(CategoryTreeDTO::getDisplayOrder));
        return topLevel;
    }

    private CategoryTreeDTO findNode(List<CategoryTreeDTO> nodes, Long categoryId) {
        for (CategoryTreeDTO node : nodes) {
            if (node.getId().equals(categoryId)) {
                return node;
            }
            CategoryTreeDTO found = findNode(node.getChildren(), categoryId);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    /**
     * Рекурсивно обходит категорию и её детей: пишет заголовок категории (лениво, перед первым товаром)
     * и товарные строки. Пустые ветки не выводятся. Дедуп через visitedCategoryIds — на случай если
     * одна выбранная категория вложена в другую выбранную.
     *
     * @param rowIndexHolder [0] = текущая строка листа, [1] = сквозной счётчик товарных строк (rowCount)
     */
    private void writeCategoryBranch(SXSSFSheet sheet, Map<String, CellStyle> styles, CategoryTreeDTO node,
                                      int level, boolean isB2B, int[] rowIndexHolder, Set<Long> visitedCategoryIds,
                                      Map<Integer, CellStyle> categoryStylesByLevel) {
        if (!visitedCategoryIds.add(node.getId())) {
            return;
        }

        boolean[] headerWritten = {false};

        int page = 0;
        Page<Product> productPage;
        do {
            productPage = productRepository.findByCategoryIdAndIsActiveTrue(
                    node.getId(), PageRequest.of(page, GENERATION_PAGE_SIZE));

            for (Product product : productPage.getContent()) {
                if (!headerWritten[0]) {
                    writeCategoryHeaderRow(sheet, styles, node.getName(), level, rowIndexHolder, categoryStylesByLevel);
                    headerWritten[0] = true;
                }
                writeProductRow(sheet, styles, product, isB2B, rowIndexHolder);
            }

            entityManager.clear();
            page++;
        } while (productPage.hasNext());

        for (CategoryTreeDTO child : node.getChildren()) {
            writeCategoryBranch(sheet, styles, child, level + 1, isB2B, rowIndexHolder, visitedCategoryIds,
                    categoryStylesByLevel);
        }
    }

    private void writeCategoryHeaderRow(SXSSFSheet sheet, Map<String, CellStyle> styles, String categoryName,
                                         int level, int[] rowIndexHolder, Map<Integer, CellStyle> categoryStylesByLevel) {
        Row row = sheet.createRow(rowIndexHolder[0]++);
        CellStyle style = categoryStylesByLevel.computeIfAbsent(level, lvl -> {
            XSSFCellStyle cloned = (XSSFCellStyle) sheet.getWorkbook().createCellStyle();
            cloned.cloneStyleFrom(styles.get("category"));
            cloned.setIndention((short) (int) lvl);
            return cloned;
        });
        writeCell(row, COL_NAME, categoryName, style);
    }

    private void writeProductRow(SXSSFSheet sheet, Map<String, CellStyle> styles, Product product,
                                  boolean isB2B, int[] rowIndexHolder) {
        Row row = sheet.createRow(rowIndexHolder[0]++);
        BigDecimal price = isB2B ? product.getPrice() : product.getWholesalePrice();
        int number = ++rowIndexHolder[1];

        writeCell(row, COL_NUMBER, String.valueOf(number), styles.get("number"));
        writeCell(row, COL_SKU, product.getSku(), styles.get("productCell"));
        writeCell(row, COL_NAME, product.getName(), styles.get("productName"));
        writeCell(row, COL_UNIT, product.getUnitOfMeasure(), styles.get("unit"));
        boolean hasPrice = price != null && price.signum() > 0;
        writeCell(row, COL_PRICE, hasPrice ? price.toPlainString() : PRICE_ON_REQUEST, styles.get("price"));
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
