package ru.rfsnab.integrationservice.service.ftk;

import lombok.extern.slf4j.Slf4j;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.springframework.stereotype.Component;
import ru.rfsnab.integrationservice.model.ftk.FtkProduct;
import ru.rfsnab.integrationservice.model.ftk.FtkProduct.FtkVariant;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Парсер XLS-выгрузки ФТК (HSSF формат — .xls, не .xlsx).
 *
 * Структура файла:
 *   - Строка категории:  A="01 Спецодежда", остальные пустые  → уровень определяется по числовому префиксу или отступу
 *   - Строка товара:     A=артикул (без точки), B=наименование, C=цена, D=URL изображения
 *   - Строка варианта:   A=артикул.NNN,         B=характеристики (JSON или ключ:значение), C=цена, D=URL
 *
 * Если у товара нет вариантов (нет строк .NNN) — создаётся без явных вариантов.
 */
@Component
@Slf4j
public class FtkXlsParser {

    /**
     * Индексы колонок в XLS-выгрузке ФТК.
     * Уточняются под реальную структуру файла.
     */
    private static final int COL_ARTICLE  = 0;  // Артикул / наименование категории
    private static final int COL_NAME     = 1;  // Наименование товара
    private static final int COL_PRICE    = 2;  // Цена (розничная)
    private static final int COL_IMAGE    = 3;  // URL изображения
    private static final int COL_ATTR1_KEY   = 4;  // Ключ первого атрибута (например "Размер")
    private static final int COL_ATTR1_VAL   = 5;  // Значение первого атрибута (например "XL")
    private static final int COL_ATTR2_KEY   = 6;  // Ключ второго атрибута (например "Рост")
    private static final int COL_ATTR2_VAL   = 7;  // Значение второго атрибута (например "170-176")

    /**
     * Парсит XLS из InputStream.
     * Stream не закрывается здесь — ответственность вызывающего.
     *
     * @param xls входной поток XLS файла
     * @return список товаров ФТК с вариантами
     */
    public List<FtkProduct> parse(InputStream xls) throws IOException {
        List<FtkProduct> result = new ArrayList<>();

        try (HSSFWorkbook workbook = new HSSFWorkbook(xls)) {
            Sheet sheet = workbook.getSheetAt(0);
            log.info("Парсинг XLS ФТК: {} строк на листе '{}'", sheet.getLastRowNum(), sheet.getSheetName());

            // Стек текущего пути категорий
            Deque<String> categoryStack = new ArrayDeque<>();
            // Текущий товар (родитель) и его варианты-накопители
            BuildContext ctx = null;

            for (int rowIdx = sheet.getFirstRowNum(); rowIdx <= sheet.getLastRowNum(); rowIdx++) {
                Row row = sheet.getRow(rowIdx);
                if (row == null) continue;

                String articleCell = getStringValue(row, COL_ARTICLE).trim();
                if (articleCell.isEmpty()) continue;

                if (isCategoryRow(row)) {
                    // Завершаем предыдущий товар перед переходом к новой категории
                    if (ctx != null) {
                        result.add(ctx.build());
                        ctx = null;
                    }
                    updateCategoryStack(categoryStack, articleCell);
                    continue;
                }

                if (isVariantRow(articleCell)) {
                    // Строка варианта — добавляем к текущему родителю
                    if (ctx != null) {
                        FtkVariant variant = parseVariant(row, articleCell);
                        if (variant != null) ctx.addVariant(variant);
                    } else {
                        log.warn("Строка варианта без родителя: {}, row={}", articleCell, rowIdx);
                    }
                    continue;
                }

                // Строка товара — завершаем предыдущий, начинаем новый
                if (ctx != null) {
                    result.add(ctx.build());
                }
                ctx = parseProductRow(row, articleCell, buildCategoryPath(categoryStack));
            }

            // Последний незакрытый товар
            if (ctx != null) {
                result.add(ctx.build());
            }
        }

        log.info("Распарсено {} товаров ФТК", result.size());
        return result;
    }

    // ===== Category =====

    /**
     * Строка категории: первая ячейка не пустая, все остальные значимые ячейки (B-D) — пустые.
     * Дополнительный признак: артикул начинается с цифры и содержит пробел (например "01 Спецодежда").
     */
    private boolean isCategoryRow(Row row) {
        String article = getStringValue(row, COL_ARTICLE).trim();
        if (article.isEmpty()) return false;
        // Нет цены и нет имени в B-колонке
        boolean noName = getStringValue(row, COL_NAME).isBlank();
        boolean noPrice = getStringValue(row, COL_PRICE).isBlank();
        // Категория выглядит как "NN Название" или просто "Название" без точки
        boolean looksLikeCategory = noName && noPrice;
        return looksLikeCategory;
    }

    /**
     * Строка варианта: артикул содержит точку ("87490974.001").
     */
    private boolean isVariantRow(String article) {
        return article.contains(".");
    }

    /**
     * Обновляет стек категорий. Простая эвристика: новая категория — сброс стека,
     * поскольку ФТК выгружает плоский список с иерархическими заголовками.
     * TODO: доработать под реальную вложенность после просмотра файла.
     */
    private void updateCategoryStack(Deque<String> stack, String categoryCell) {
        // Убираем числовой префикс ("01 Спецодежда" → "Спецодежда")
        String categoryName = categoryCell.replaceFirst("^\\d+\\s+", "").trim();
        if (categoryName.isEmpty()) return;
        // Определяем уровень вложенности по отступу (пробелы в начале оригинала)
        // или по количеству пробелов перед текстом — простой вариант: просто используем плоский стек
        stack.clear();
        stack.push(categoryName);
        log.debug("Категория: {}", categoryName);
    }

    private String buildCategoryPath(Deque<String> stack) {
        if (stack.isEmpty()) return "";
        // Стек — LIFO, нам нужен порядок от корня к листу
        List<String> path = new ArrayList<>(stack);
        java.util.Collections.reverse(path);
        return String.join(" > ", path);
    }

    // ===== Product =====

    private BuildContext parseProductRow(Row row, String article, String categoryPath) {
        String name = getStringValue(row, COL_NAME).trim();
        if (name.isEmpty()) {
            // Иногда наименование совпадает с категорией — пропускаем
            log.debug("Пропуск строки без наименования: article={}", article);
            return null;
        }
        BigDecimal price = parseBigDecimal(getStringValue(row, COL_PRICE), article);
        String imageUrl = getStringValue(row, COL_IMAGE).trim();

        return new BuildContext(
                article, name, categoryPath, price, imageUrl.isEmpty() ? null : imageUrl
        );
    }

    // ===== Variant =====

    private FtkVariant parseVariant(Row row, String article) {
        BigDecimal price = parseBigDecimal(getStringValue(row, COL_PRICE), article);
        String imageUrl = getStringValue(row, COL_IMAGE).trim();

        Map<String, String> attrs = new LinkedHashMap<>();
        addAttr(attrs, row, COL_ATTR1_KEY, COL_ATTR1_VAL);
        addAttr(attrs, row, COL_ATTR2_KEY, COL_ATTR2_VAL);

        if (attrs.isEmpty()) {
            // Вариант без атрибутов — пропускаем, он не несёт информации для покупателя
            log.debug("Вариант без атрибутов: {}", article);
            return null;
        }

        return FtkVariant.builder()
                .article(article)
                .price(price)
                .imageUrl(imageUrl.isEmpty() ? null : imageUrl)
                .attributes(attrs)
                .build();
    }

    private void addAttr(Map<String, String> attrs, Row row, int keyCol, int valCol) {
        String key = getStringValue(row, keyCol).trim();
        String val = getStringValue(row, valCol).trim();
        if (!key.isEmpty() && !val.isEmpty()) {
            attrs.put(key, val);
        }
    }

    // ===== Utilities =====

    private String getStringValue(Row row, int col) {
        Cell cell = row.getCell(col);
        if (cell == null) return "";
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> {
                double d = cell.getNumericCellValue();
                // Целое число — без дробной части
                yield (d == Math.floor(d)) ? String.valueOf((long) d) : String.valueOf(d);
            }
            case FORMULA -> {
                try {
                    yield cell.getStringCellValue();
                } catch (Exception e) {
                    yield String.valueOf(cell.getNumericCellValue());
                }
            }
            default -> "";
        };
    }

    private BigDecimal parseBigDecimal(String value, String context) {
        if (value == null || value.isBlank()) return null;
        try {
            return new BigDecimal(value.trim().replace(",", ".").replace(" ", ""));
        } catch (NumberFormatException e) {
            log.warn("Невалидная цена '{}' для {}", value, context);
            return null;
        }
    }

    // ===== Builder context =====

    private static class BuildContext {
        private final String article;
        private final String name;
        private final String categoryPath;
        private final BigDecimal price;
        private final String imageUrl;
        private final List<FtkVariant> variants = new ArrayList<>();

        BuildContext(String article, String name, String categoryPath,
                     BigDecimal price, String imageUrl) {
            this.article = article;
            this.name = name;
            this.categoryPath = categoryPath;
            this.price = price;
            this.imageUrl = imageUrl;
        }

        void addVariant(FtkVariant v) {
            variants.add(v);
        }

        FtkProduct build() {
            return FtkProduct.builder()
                    .article(article)
                    .name(name)
                    .categoryPath(categoryPath)
                    .price(price)
                    .imageUrl(imageUrl)
                    .variants(new ArrayList<>(variants))
                    .build();
        }
    }
}
