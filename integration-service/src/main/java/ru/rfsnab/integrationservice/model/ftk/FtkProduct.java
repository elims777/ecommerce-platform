package ru.rfsnab.integrationservice.model.ftk;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Промежуточная модель товара ФТК, извлечённая из XLS.
 * Родительская строка (без точки в артикуле) → parent product.
 * Дочерние строки (артикул.001, .002, ...) → variants.
 */
@Getter
@Builder
public class FtkProduct {

    /** Артикул родителя (без суффикса, например "87490974") */
    private final String article;

    /** Наименование товара */
    private final String name;

    /** Путь категории ("Спецодежда > Костюмы > ...") */
    private final String categoryPath;

    /** Розничная цена родителя (используется если нет вариантов) */
    private final BigDecimal price;

    /** URL изображения родителя на FTP */
    private final String imageUrl;

    /** Варианты (размер/рост) — пустой список если товар без вариантов */
    private final List<FtkVariant> variants;

    @Getter
    @Builder
    public static class FtkVariant {
        /** Артикул варианта, например "87490974.001" */
        private final String article;

        /** Розничная цена варианта */
        private final BigDecimal price;

        /** URL изображения варианта (может совпадать с родителем) */
        private final String imageUrl;

        /** Атрибуты: {"Размер": "XL", "Рост": "170-176"} */
        private final Map<String, String> attributes;
    }
}
