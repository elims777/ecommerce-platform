package ru.rfsnab.integrationservice.model.commerceml;

import java.util.Map;

/**
 * Справочник единиц измерения по ОКЕИ для формирования БазоваяЕдиница в выгрузке заказа.
 * Позволяет 1С создать номенклатуру с корректной единицей.
 * Ключ — краткое наименование единицы (как хранится в product-service, регистронезависимо).
 */
public final class OkeiUnits {

    private OkeiUnits() {}

    /** Единица по умолчанию, если в товаре не указана или неизвестна. */
    private static final BaseUnit DEFAULT = unit("796", "Штука", "PCE", "шт");

    private static final Map<String, BaseUnit> UNITS = Map.ofEntries(
            Map.entry("шт", unit("796", "Штука", "PCE", "шт")),
            Map.entry("шт.", unit("796", "Штука", "PCE", "шт")),
            Map.entry("кг", unit("166", "Килограмм", "KGM", "кг")),
            Map.entry("г", unit("163", "Грамм", "GRM", "г")),
            Map.entry("т", unit("168", "Тонна", "TNE", "т")),
            Map.entry("л", unit("112", "Литр", "LTR", "л")),
            Map.entry("м", unit("006", "Метр", "MTR", "м")),
            Map.entry("м2", unit("055", "Квадратный метр", "MTK", "м2")),
            Map.entry("м3", unit("113", "Кубический метр", "MTQ", "м3")),
            Map.entry("пог.м", unit("018", "Погонный метр", "018", "пог.м")),
            Map.entry("упак", unit("778", "Упаковка", "NMP", "упак")),
            Map.entry("упак.", unit("778", "Упаковка", "NMP", "упак")),
            Map.entry("компл", unit("839", "Комплект", "SET", "компл")),
            Map.entry("компл.", unit("839", "Комплект", "SET", "компл")),
            Map.entry("пара", unit("715", "Пара", "PR", "пара")),
            Map.entry("рулон", unit("736", "Рулон", "RO", "рулон")),
            Map.entry("лист", unit("625", "Лист", "625", "лист")),
            Map.entry("набор", unit("704", "Набор", "SET", "набор"))
    );

    /**
     * Возвращает БазоваяЕдиница по краткому наименованию единицы.
     * Неизвестную единицу отдаёт текстом без кода ОКЕИ (1С подставит значение по умолчанию),
     * пустую/null — как «шт».
     */
    public static BaseUnit resolve(String unitOfMeasure) {
        if (unitOfMeasure == null || unitOfMeasure.isBlank()) {
            return DEFAULT;
        }
        String key = unitOfMeasure.trim().toLowerCase();
        BaseUnit known = UNITS.get(key);
        if (known != null) {
            return known;
        }
        // Неизвестная единица: отдаём как есть, без кода ОКЕИ.
        BaseUnit unknown = new BaseUnit();
        unknown.setValue(unitOfMeasure.trim());
        return unknown;
    }

    private static BaseUnit unit(String code, String fullName, String intlAbbr, String value) {
        BaseUnit u = new BaseUnit();
        u.setCode(code);
        u.setFullName(fullName);
        u.setInternationalAbbreviation(intlAbbr);
        u.setValue(value);
        return u;
    }
}
