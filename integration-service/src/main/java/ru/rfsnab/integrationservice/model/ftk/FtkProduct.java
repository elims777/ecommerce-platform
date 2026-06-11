package ru.rfsnab.integrationservice.model.ftk;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Getter
@Builder
public class FtkProduct {

    /** UUID товара из import (Товар.Ид) */
    private final String productUuid;

    /** Артикул товара без точки, например "87490974" */
    private final String article;

    /** Наименование товара */
    private final String name;

    /** Текстовое описание */
    private final String description;

    /** Путь категории ("Спецодежда > Спецодежда летняя > Костюмы летние") */
    private final String categoryPath;

    /** Относительный путь к картинке от goods/1/, например "import_files/b5/UUID.jpg" */
    private final String imagePath;

    /** Варианты — пустой список если товар без вариантов */
    private final List<FtkVariant> variants;

    @Getter
    @Builder
    public static class FtkVariant {
        /** Составной Ид предложения: UUID_товара#UUID_варианта или просто UUID для простого товара */
        private final String offerUuid;

        /** Артикул варианта: "87490974.001"; для простого товара = article без точки */
        private final String article;

        /** Розничная цена (из prices, ИдТипаЦены = fdf5831f-...) */
        private final BigDecimal price;

        /** Количество в наличии (из rests) */
        private final int stockQuantity;

        /** Атрибуты из ХарактеристикиТовара: {"Размер": "9", "Основной цвет": "синий"} — только непустые */
        private final Map<String, String> attributes;

        /** Ставка НДС в процентах (из offers, СтавкиНалогов) */
        private final Integer vatRate;
    }
}
