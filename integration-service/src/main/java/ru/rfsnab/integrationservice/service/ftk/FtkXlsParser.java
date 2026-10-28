package ru.rfsnab.integrationservice.service.ftk;

import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Component;
import ru.rfsnab.integrationservice.model.ftk.FtkProduct;
import ru.rfsnab.integrationservice.model.ftk.FtkProduct.FtkVariant;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Парсер XLS-выгрузки ФТК (HSSF формат — .xls).
 *
 * Колонки:
 *   A(0) — наименование товара или название группы/категории
 *   B(1) — артикул (пустой у категорий; содержит точку у вариантов: "87490974.001")
 *   C(2) — цена
 *   D(3) — единица измерения
 *   E(4) — наименование для печати
 *   F(5) — текстовое описание
 *   I(8) — основной материал
 *   M(12) — ссылка на изображение (FTP URL)
 *
 * Строки 0-6 (1-7 в Excel) — заголовки, пропускаются.
 *
 * Иерархия категорий определяется по числовому префиксу в колонке A:
 *   "01 Спецодежда"          → уровень 1
 *   "01.01 Спецодежда летняя" → уровень 2
 *   "01.01.1 Костюмы летние"  → уровень 3
 */
@Component
@Slf4j
public class FtkXlsParser {

    private static final int HEADER_ROWS   = 7;   // строки 1-7 в Excel — пропустить

    private static final int COL_NAME      = 0;   // A-C (merged) — наименование / группа / характеристика
    private static final int COL_ARTICLE   = 3;   // D — артикул
    private static final int COL_PRICE     = 4;   // E — цена
    private static final int COL_DESC      = 8;   // I — текстовое описание
    private static final int COL_IMAGE     = 12;  // M — ссылка на изображение

    public List<FtkProduct> parse(InputStream xls) throws IOException {
        List<FtkProduct> result = new ArrayList<>();

        try (var workbook = WorkbookFactory.create(xls)) {
            Sheet sheet = workbook.getSheetAt(0);
            log.info("Парсинг XLS ФТК: {} строк", sheet.getLastRowNum());

            BuildContext ctx = null;

            for (int rowIdx = HEADER_ROWS; rowIdx <= sheet.getLastRowNum(); rowIdx++) {
                Row row = sheet.getRow(rowIdx);
                if (row == null) continue;

                String nameCell    = getStringValue(row, COL_NAME).trim();
                String articleCell = getStringValue(row, COL_ARTICLE).trim();

                if (nameCell.isEmpty() && articleCell.isEmpty()) continue;

                // Категория: артикул пустой, в A есть текст
                if (articleCell.isEmpty()) {
                    if (ctx != null) {
                        result.add(ctx.build());
                        ctx = null;
                    }
                    int depth = detectCategoryDepth(nameCell);
                    String catName = stripPrefix(nameCell);
                    log.debug("Категория уровень {}: {}", depth, catName);
                    continue;
                }

                // Вариант: артикул содержит точку
                if (articleCell.contains(".")) {
                    if (ctx != null) {
                        FtkVariant variant = parseVariant(row, articleCell, nameCell);
                        ctx.addVariant(variant);
                    } else {
                        log.warn("Вариант без родителя: {}, row={}", articleCell, rowIdx);
                    }
                    continue;
                }

                // Товар
                if (ctx != null) {
                    result.add(ctx.build());
                }
                ctx = parseProductRow(row, articleCell, nameCell);
            }

            if (ctx != null) {
                result.add(ctx.build());
            }
        }

        log.info("Распарсено {} товаров ФТК", result.size());
        return result;
    }

    /**
     * Определяет уровень вложенности категории по числовому префиксу.
     * "01 ..."       → 1
     * "01.01 ..."    → 2
     * "01.01.1 ..."  → 3
     */
    private int detectCategoryDepth(String nameCell) {
        String prefix = nameCell.split("\\s+")[0];
        if (!prefix.matches("[\\d.]+")) return 1;
        // Количество точек в префиксе + 1 = уровень
        long dots = prefix.chars().filter(c -> c == '.').count();
        return (int) dots + 1;
    }

    /**
     * Убирает числовой префикс: "01.01 Спецодежда летняя" → "Спецодежда летняя".
     */
    private String stripPrefix(String nameCell) {
        return nameCell.replaceFirst("^[\\d.]+\\s*", "").trim();
    }

    private BuildContext parseProductRow(Row row, String article, String name) {
        BigDecimal price   = parseBigDecimal(getStringValue(row, COL_PRICE), article);
        String description = getStringValue(row, COL_DESC).trim();
        String imagePath   = getStringValue(row, COL_IMAGE).trim();

        return new BuildContext(
                article,
                name,
                description.isEmpty() ? null : description,
                price,
                imagePath.isEmpty() ? null : imagePath
        );
    }

    private FtkVariant parseVariant(Row row, String article, String characteristic) {
        BigDecimal price = parseBigDecimal(getStringValue(row, COL_PRICE), article);
        Map<String, String> attrs = parseCharacteristic(characteristic);

        return FtkVariant.builder()
                .offerUuid(null)
                .article(article)
                .price(price)
                .stockQuantity(0)
                .attributes(attrs)
                .vatRate(null)
                .barcode(null)
                .countryOfOrigin(null)
                .deleted(false)
                .build();
    }

    /**
     * Разбирает характеристику варианта из колонки A.
     * Формат: "44-46; 170-176" → {"Размер": "44-46", "Рост": "170-176"}
     * Если не удаётся разобрать — возвращает {"Характеристика": originalValue}.
     */
    private Map<String, String> parseCharacteristic(String characteristic) {
        Map<String, String> attrs = new LinkedHashMap<>();
        if (characteristic == null || characteristic.isBlank()) return attrs;

        String[] parts = characteristic.split(";");
        if (parts.length == 2) {
            attrs.put("Размер", parts[0].trim());
            attrs.put("Рост",   parts[1].trim());
        } else {
            attrs.put("Характеристика", characteristic.trim());
        }
        return attrs;
    }

    private String getStringValue(Row row, int col) {
        Cell cell = row.getCell(col);
        if (cell == null) return "";
        return switch (cell.getCellType()) {
            case STRING  -> cell.getStringCellValue();
            case NUMERIC -> {
                double d = cell.getNumericCellValue();
                yield (d == Math.floor(d)) ? String.valueOf((long) d) : String.valueOf(d);
            }
            case FORMULA -> {
                try { yield cell.getStringCellValue(); }
                catch (Exception e) { yield String.valueOf(cell.getNumericCellValue()); }
            }
            default -> "";
        };
    }

    private BigDecimal parseBigDecimal(String value, String context) {
        if (value == null || value.isBlank()) return null;
        try {
            return new BigDecimal(value.trim().replace(",", ".").replace(" ", "").replace("\u00A0", ""));
        } catch (NumberFormatException e) {
            log.warn("Невалидная цена '{}' для {}", value, context);
            return null;
        }
    }

    private static class BuildContext {
        private final String article;
        private final String name;
        private final String description;
        private final BigDecimal price;
        private final String imagePath;
        private final List<FtkVariant> variants = new ArrayList<>();

        BuildContext(String article, String name, String description,
                     BigDecimal price, String imagePath) {
            this.article     = article;
            this.name        = name;
            this.description = description;
            this.price       = price;
            this.imagePath   = imagePath;
        }

        void addVariant(FtkVariant v) { variants.add(v); }

        FtkProduct build() {
            List<FtkVariant> effectiveVariants = variants.isEmpty()
                    ? List.of(FtkVariant.builder()
                            .offerUuid(null)
                            .article(article)
                            .price(price)
                            .stockQuantity(0)
                            .attributes(Map.of())
                            .vatRate(null)
                            .barcode(null)
                            .countryOfOrigin(null)
                            .deleted(false)
                            .build())
                    : new ArrayList<>(variants);

            return FtkProduct.builder()
                    .productUuid(null)
                    .article(article)
                    .name(name)
                    .description(description)
                    .groupUuid(null)
                    .imagePaths(imagePath != null && !imagePath.isEmpty() ? List.of(imagePath) : List.of())
                    .properties(Map.of())
                    .unitOfMeasure(null)
                    .variants(effectiveVariants)
                    .build();
        }
    }
}
