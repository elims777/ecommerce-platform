package ru.rfsnab.orderservice.delivery.client;

import com.fasterxml.jackson.databind.JsonNode;
import ru.rfsnab.orderservice.delivery.exception.CarrierApiException;
import ru.rfsnab.orderservice.delivery.models.entity.enums.PointType;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;

/**
 * Терпимое чтение JSON ответов ТК: лишние и отсутствующие поля не ломают разбор.
 */
final class CarrierJson {

    private static final String[] NESTED_NAME_FIELDS = {"name", "title"};
    private static final int DATE_LENGTH = "2026-10-12".length();

    private CarrierJson() {
    }

    /**
     * Первое непустое значение среди полей names; вложенный объект даёт своё name/title.
     *
     * @return текст или null
     */
    static String text(JsonNode node, String... names) {
        if (node == null) {
            return null;
        }
        for (String name : names) {
            JsonNode value = node.get(name);
            if (value == null || value.isNull()) {
                continue;
            }
            String result = null;
            if (value.isObject()) {
                result = text(value, NESTED_NAME_FIELDS);
            } else if (value.isValueNode()) {
                result = value.asText().trim();
            }
            if (result != null && !result.isEmpty()) {
                return result;
            }
        }
        return null;
    }

    /**
     * Первое числовое значение среди полей names (число или строка, запятая как разделитель допустима).
     *
     * @return число или null
     */
    static Double number(JsonNode node, String... names) {
        if (node == null) {
            return null;
        }
        for (String name : names) {
            JsonNode value = node.get(name);
            if (value == null || value.isNull()) {
                continue;
            }
            if (value.isNumber()) {
                return value.doubleValue();
            }
            if (value.isTextual()) {
                try {
                    return Double.parseDouble(value.asText().trim().replace(',', '.'));
                } catch (NumberFormatException ignored) {
                    // не число — пробуем следующее поле; запись без координат отсечёт валидация синхронизации
                }
            }
        }
        return null;
    }
    /**
     * Первое десятичное значение среди полей names: JSON-число или строка ("43.0", запятая допустима).
     *
     * @return число или null
     */
    static BigDecimal decimal(JsonNode node, String... names) {
        if (node == null) {
            return null;
        }
        for (String name : names) {
            JsonNode value = node.get(name);
            if (value == null || value.isNull()) {
                continue;
            }
            if (value.isNumber()) {
                return value.decimalValue();
            }
            if (value.isTextual()) {
                try {
                    return new BigDecimal(value.asText().trim().replace(',', '.'));
                } catch (NumberFormatException ignored) {
                    // не число — пробуем следующее поле
                }
            }
        }
        return null;
    }

    /** Целое из {@link #decimal}: ТК присылают количество мест то числом, то строкой, то "3.0". */
    static Integer integer(JsonNode node, String... names) {
        BigDecimal value = decimal(node, names);
        return value == null ? null : value.intValue();
    }

    /** Логическое поле; null, если поля нет или оно не boolean. */
    static Boolean bool(JsonNode node, String name) {
        JsonNode value = node == null ? null : node.get(name);
        return value != null && value.isBoolean() ? value.booleanValue() : null;
    }

    /**
     * Первая разбираемая дата среди полей names: ISO-момент, дата-время без пояса (считается UTC) или только дата.
     *
     * @return момент или null
     */
    static Instant instant(JsonNode node, String... names) {
        String value = text(node, names);
        if (value == null) {
            return null;
        }
        try {
            return OffsetDateTime.parse(value).toInstant();
        } catch (DateTimeParseException ignored) {
            // не момент с поясом — пробуем без пояса
        }
        try {
            return LocalDateTime.parse(value).toInstant(ZoneOffset.UTC);
        } catch (DateTimeParseException ignored) {
            // не дата-время — пробуем только дату
        }
        try {
            return LocalDate.parse(value.length() > DATE_LENGTH ? value.substring(0, DATE_LENGTH) : value)
                    .atStartOfDay().toInstant(ZoneOffset.UTC);
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    /**
     * Массив точек: сам root или первый массив во вложенных полях names.
     *
     * @throws CarrierApiException если массива нет — ответ не разобрался целиком
     */
    static JsonNode array(JsonNode root, String... names) {
        JsonNode found = findArray(root, names);
        if (found == null) {
            throw new CarrierApiException("Ответ ТК не содержит списка точек");
        }
        return found;
    }

    private static JsonNode findArray(JsonNode node, String... names) {
        if (node == null) {
            return null;
        }
        if (node.isArray()) {
            return node;
        }
        for (String name : names) {
            JsonNode found = findArray(node.get(name), names);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    /**
     * Точка типа TERMINAL по самым вероятным именам полей — для ТК, чей формат ответа сверен не по реальному ответу.
     */
    static TerminalData genericTerminal(JsonNode item) {
        return new TerminalData(text(item, "id", "code", "guid", "branchId"), PointType.TERMINAL,
                text(item, "city", "cityName", "town"), text(item, "name", "title"),
                text(item, "address", "fullAddress"), number(item, "latitude", "lat"),
                number(item, "longitude", "lon", "lng"), text(item, "workTime", "worktime", "schedule", "workingHours"),
                text(item, "phone", "phones"));
    }
}
