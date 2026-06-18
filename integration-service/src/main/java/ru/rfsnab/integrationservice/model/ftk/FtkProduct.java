package ru.rfsnab.integrationservice.model.ftk;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Getter
@Builder
public class FtkProduct {

    private final String productUuid;
    private final String article;
    private final String name;
    private final String description;

    /** UUID группы из классификатора (для построения дерева категорий). */
    private final String groupUuid;

    /** Все изображения товара (пути относительно goods/1/). */
    private final List<String> imagePaths;

    /** Расшифрованные свойства из ЗначенияСвойств: имя → значение. */
    private final Map<String, String> properties;

    /** Текстовое обозначение единицы измерения ("шт", "пар" и т.д.). */
    private final String unitOfMeasure;

    private final List<FtkVariant> variants;

    @Getter
    @Builder
    public static class FtkVariant {
        private final String offerUuid;
        private final String article;
        private final BigDecimal price;
        private final int stockQuantity;
        private final Map<String, String> attributes;
        private final Integer vatRate;
        private final String barcode;
        private final String countryOfOrigin;
        /** true если товар помечен к удалению (ПометкаУдаления). */
        private final boolean deleted;
    }
}
