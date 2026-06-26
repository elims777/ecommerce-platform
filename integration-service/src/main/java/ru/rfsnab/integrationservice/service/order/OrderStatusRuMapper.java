package ru.rfsnab.integrationservice.service.order;

import java.util.Map;

/**
 * Маппинг русских названий состояний заказа из 1С УНФ → код OrderStatus.
 * <p>
 * 1С присылает в <Значение> русское название состояния документа (см. таблицу
 * "Состояния заказа покупателя" в УНФ): "Счёт выставлен", "Оплачен", "Отгружен" и т.п.
 * В order-service статусы хранятся кодом enum (PROCESSING, INVOICE_SENT, ...).
 * <p>
 * Маппинг case-insensitive, ё нормализуется в е.
 */
public final class OrderStatusRuMapper {

    private static final Map<String, String> RU_TO_CODE = Map.ofEntries(
            Map.entry("заказ создан",            "CREATED"),
            Map.entry("в работе",                "PROCESSING"),
            Map.entry("счет выставлен",          "INVOICE_SENT"),
            Map.entry("ожидает оплаты",          "PENDING_PAYMENT"),
            Map.entry("ожидает подтверждения",   "AWAITING_CONFIRMATION"),
            Map.entry("оплачен",                 "PAID"),
            Map.entry("оплачен частично",        "PARTIALLY_PAID"),
            Map.entry("ошибка оплаты",           "PAYMENT_FAILED"),
            Map.entry("отгружен",                "SHIPPED"),
            Map.entry("в пути",                  "IN_TRANSIT"),
            Map.entry("доставлен",               "DELIVERED"),
            Map.entry("отменен",                 "CANCELLED"),
            Map.entry("возврат средств",         "REFUNDED"),
            Map.entry("завершен",                "COMPLETED")
    );

    private OrderStatusRuMapper() {}

    /**
     * @param russianName значение тега <Значение> из 1С (например, "Счёт выставлен")
     * @return код OrderStatus (например, "INVOICE_SENT")
     * @throws IllegalArgumentException если значение неизвестно
     */
    public static String toCode(String russianName) {
        if (russianName == null || russianName.isBlank()) {
            throw new IllegalArgumentException("Пустое значение статуса от 1С");
        }
        String key = russianName.trim().toLowerCase().replace('ё', 'е');
        String code = RU_TO_CODE.get(key);
        if (code == null) {
            throw new IllegalArgumentException("Неизвестный статус от 1С: '" + russianName + "'");
        }
        return code;
    }
}
