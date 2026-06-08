package ru.rfsnab.integrationservice.model.ftk;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Getter
@Builder
public class FtkProduct {

    /** Артикул родителя (без суффикса, например "87490974") */
    private final String article;

    /** Наименование товара (колонка A) */
    private final String name;

    /** Наименование для печати (колонка E) */
    private final String printName;

    /** Текстовое описание (колонка F) */
    private final String description;

    /** Основной материал (колонка I) */
    private final String material;

    /** Путь категории ("Спецодежда > Спецодежда летняя > Костюмы летние") */
    private final String categoryPath;

    /** Розничная цена родителя */
    private final BigDecimal price;

    /** URL изображения на FTP (колонка M) */
    private final String imageUrl;

    /** Варианты (размер/рост) — пустой список если товар без вариантов */
    private final List<FtkVariant> variants;

    @Getter
    @Builder
    public static class FtkVariant {
        /** Артикул варианта, например "87490974.001" */
        private final String article;

        /** Характеристика варианта из колонки A, например "44-46; 170-176" */
        private final String characteristic;

        /** Розничная цена варианта */
        private final BigDecimal price;

        /** Атрибуты, разобранные из characteristic: {"Размер": "44-46", "Рост": "170-176"} */
        private final Map<String, String> attributes;
    }
}
