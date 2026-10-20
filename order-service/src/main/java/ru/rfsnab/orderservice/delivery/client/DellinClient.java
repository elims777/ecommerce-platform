package ru.rfsnab.orderservice.delivery.client;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import ru.rfsnab.orderservice.delivery.exception.CarrierApiException;
import ru.rfsnab.orderservice.delivery.models.entity.ShipmentDocument;
import ru.rfsnab.orderservice.delivery.models.entity.ShipmentPackage;
import ru.rfsnab.orderservice.delivery.models.entity.ShipmentPayer;
import ru.rfsnab.orderservice.delivery.models.entity.enums.CarrierCode;
import ru.rfsnab.orderservice.delivery.models.entity.enums.PointType;
import ru.rfsnab.orderservice.delivery.models.entity.enums.ShipmentStatusCode;

import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import static ru.rfsnab.orderservice.delivery.client.CarrierJson.bool;
import static ru.rfsnab.orderservice.delivery.client.CarrierJson.decimal;
import static ru.rfsnab.orderservice.delivery.client.CarrierJson.instant;
import static ru.rfsnab.orderservice.delivery.client.CarrierJson.integer;
import static ru.rfsnab.orderservice.delivery.client.CarrierJson.number;
import static ru.rfsnab.orderservice.delivery.client.CarrierJson.text;

/**
 * Деловые Линии: /v3/public/terminals.json (appkey) отдаёт {hash, url}; файл по url качается,
 * только если hash изменился с прошлой загрузки.
 */
@Slf4j
@Component
public class DellinClient implements CarrierClient {

    private static final String TERMINALS_PATH = "/v3/public/terminals.json";
    private static final String GENERAL_DEPARTMENT = "general";
    private static final Map<String, String> DAYS = new LinkedHashMap<>();

    static {
        DAYS.put("monday", "Пн");
        DAYS.put("tuesday", "Вт");
        DAYS.put("wednesday", "Ср");
        DAYS.put("thursday", "Чт");
        DAYS.put("friday", "Пт");
        DAYS.put("saturday", "Сб");
        DAYS.put("sunday", "Вс");
    }

    private static final String LOGIN_PATH = "/v3/auth/login.json";
    private static final String JOURNAL_PATH = "/v3/orders.json";
    private static final String PRINT_PATH = "/v1/printable.json";
    private static final int MAX_JOURNAL_PAGES = 50;
    /** Лимит документации ДЛ: не больше 5 номеров в docIds одного запроса /v3/orders.json. */
    private static final int DOC_IDS_BATCH_SIZE = 5;
    private static final String SHIPPING_DOC_TYPE = "shipping";
    private static final String DOC_REF_SEPARATOR = "|";
    /** Режимы печати документа (availableDocs) → название для менеджера; неизвестный режим показываем как есть. */
    private static final Map<String, String> DOC_NAMES = Map.of(
            "order", "Накладная", "bill", "Счёт", "invoice", "Счёт-фактура", "giveout", "Накладная на выдачу");

    /** Таблица сопоставления состояний заказа ДЛ → ShipmentStatusCode (по справочнику ДЛ «Статусы заказа груза»). */
    private static final Map<String, ShipmentStatusCode> STATUS_CODES = new HashMap<>();

    static {
        register(ShipmentStatusCode.CREATED, "draft", "processing", "pickup", "waiting");
        // in_way — не из справочника: normalize превращает «in way» в него
        register(ShipmentStatusCode.IN_TRANSIT, "received", "received_warehousing", "inway", "in_way",
                "arrived_to_airport", "airport_warehousing", "delivery");
        register(ShipmentStatusCode.ARRIVED_AT_TERMINAL, "arrived", "warehousing");
        register(ShipmentStatusCode.DELIVERED, "finished", "accompanying_documents_return");
        register(ShipmentStatusCode.CANCELLED, "declined");
    }

    private final RestClient http;
    private final String appKey;
    private final String login;
    private final String password;
    private volatile String lastHash;

    /** Создаёт клиент; без appkey клиент отключён, без логина и пароля ЛК недоступны статусы и журнал. */
    public DellinClient(RestClient.Builder builder,
                        @Value("${carriers.dellin.base-url}") String baseUrl,
                        @Value("${carriers.dellin.appkey:}") String appKey,
                        @Value("${carriers.dellin.login:}") String login,
                        @Value("${carriers.dellin.password:}") String password) {
        this.http = builder.baseUrl(baseUrl).build();
        this.appKey = appKey;
        this.login = login;
        this.password = password;
    }

    @Override
    public boolean supportsStatusTracking() {
        return isConfigured() && StringUtils.hasText(login) && StringUtils.hasText(password);
    }

    @Override
    public boolean supportsJournal() {
        return supportsStatusTracking();
    }

    /**
     * Статусы заказов: логин в ЛК (sessionID), затем /v3/orders.json с docIds пачками по {@value #DOC_IDS_BATCH_SIZE}
     * (разбор тот же, что у журнала). Номер, которого нет в ответе, ДЛ не знает.
     */
    @Override
    public Map<String, CarrierShipmentStatus> fetchStatuses(List<String> waybills) {
        if (waybills.isEmpty()) {
            return Map.of();
        }
        String sessionId = login();
        Map<String, CarrierShipmentStatus> result = new HashMap<>();
        for (int from = 0; from < waybills.size(); from += DOC_IDS_BATCH_SIZE) {
            List<String> batch = waybills.subList(from, Math.min(from + DOC_IDS_BATCH_SIZE, waybills.size()));
            JsonNode response = http.post().uri(JOURNAL_PATH)
                    .body(Map.of("appkey", appKey, "sessionID", sessionId, "docIds", batch))
                    .retrieve().body(JsonNode.class);
            for (JsonNode order : ordersOf(response)) {
                CarrierShipmentStatus status = toStatus(order);
                for (String key : new String[]{text(order, "docId"), text(order, "orderId")}) {
                    if (key != null && waybills.contains(key)) {
                        result.put(key, status);
                    }
                }
            }
        }
        return result;
    }

    /** PDF документа: POST /v1/printable.json; ref документа — «docUID|режим», файл приходит base64 в data[0]. */
    @Override
    public byte[] downloadDocument(ShipmentDocument doc) {
        int separator = doc.ref() == null ? -1 : doc.ref().lastIndexOf(DOC_REF_SEPARATOR);
        if (separator < 0) {
            throw new CarrierApiException("Некорректная ссылка на документ Деловых Линий");
        }
        JsonNode response = http.post().uri(PRINT_PATH)
                .body(Map.of("appkey", appKey, "sessionID", login(),
                        "docUID", doc.ref().substring(0, separator), "mode", doc.ref().substring(separator + 1)))
                .retrieve().body(JsonNode.class);
        JsonNode data = response == null ? null : response.get("data");
        String base64 = data != null && data.isArray() && !data.isEmpty() ? text(data.get(0), "base64") : null;
        if (base64 == null) {
            throw new CarrierApiException("Деловые Линии не вернули файл документа");
        }
        try {
            return Base64.getMimeDecoder().decode(base64);
        } catch (IllegalArgumentException e) {
            throw new CarrierApiException("Деловые Линии вернули повреждённый файл документа");
        }
    }

    /** Журнал заказов компании с даты since: /v3/orders.json постранично, пока страницы приносят новые накладные. */
    @Override
    public List<JournalItem> fetchJournal(Instant since) {
        String sessionId = login();
        String dateStart = LocalDate.ofInstant(since, ZoneOffset.UTC).toString();
        String dateEnd = LocalDate.now(ZoneOffset.UTC).toString();
        Map<String, JournalItem> result = new LinkedHashMap<>();
        for (int page = 1; page <= MAX_JOURNAL_PAGES; page++) {
            JsonNode response = http.post().uri(JOURNAL_PATH)
                    .body(Map.of("appkey", appKey, "sessionID", sessionId,
                            "dateStart", dateStart, "dateEnd", dateEnd, "page", page))
                    .retrieve().body(JsonNode.class);
            int before = result.size();
            for (JsonNode order : ordersOf(response)) {
                String waybill = text(order, "orderId", "docId");
                if (waybill != null) {
                    result.putIfAbsent(waybill, toJournalItem(waybill, order));
                }
            }
            if (result.size() == before) {
                break;
            }
        }
        return new ArrayList<>(result.values());
    }

    /** Нормализованный код по состоянию заказа ДЛ; null, если состояние не известно таблице. */
    static ShipmentStatusCode normalizeStatus(String state) {
        return state == null ? null : STATUS_CODES.get(state.trim().toLowerCase(Locale.ROOT).replace(' ', '_'));
    }

    private String login() {
        JsonNode response = http.post().uri(LOGIN_PATH)
                .body(Map.of("appkey", appKey, "login", login, "password", password))
                .retrieve().body(JsonNode.class);
        String sessionId = text(response == null ? null : response.get("data"), "sessionID");
        if (sessionId == null) {
            throw new CarrierApiException("Деловые Линии не выдали sessionID — проверьте логин и пароль ЛК");
        }
        return sessionId;
    }

    /** Массив orders (в корне или в data); ответ без него — ошибка, а не «все накладные не найдены». */
    private static JsonNode ordersOf(JsonNode response) {
        JsonNode orders = response == null ? null : response.get("orders");
        if (orders == null && response != null && response.get("data") != null) {
            orders = response.get("data").get("orders");
        }
        if (orders == null || !orders.isArray()) {
            throw new CarrierApiException("Ответ Деловых Линий не содержит списка заказов");
        }
        return orders;
    }

    private static CarrierShipmentStatus toStatus(JsonNode order) {
        String state = text(order, "state");
        ShipmentStatusCode code = normalizeStatus(state);
        if (state != null && code == null) {
            log.warn("Деловые Линии: неизвестный статус накладной, код не меняется: {}", state);
        }
        String raw = text(order, "stateName");
        return new CarrierShipmentStatus(code, raw != null ? raw : state, cityOf(order, "derival"), cityOf(order, "arrival"),
                toCargo(order, code));
    }

    /** Груз из заказа ДЛ: числа приходят строками ("43.0"); дата выдачи — только у доставленного. */
    private static ShipmentCargo toCargo(JsonNode order, ShipmentStatusCode code) {
        JsonNode freight = order.path("freight");
        JsonNode places = order.get("cargoPlaces");
        List<ShipmentPackage> packages = places == null || !places.isArray() ? null : new ArrayList<>();
        if (packages != null) {
            places.forEach(place -> packages.add(new ShipmentPackage(text(place, "number"), null, null, null, null)));
        }
        return new ShipmentCargo(decimal(freight, "weight"), decimal(freight, "volume"), integer(freight, "places"),
                decimal(order, "totalSum"), bool(order, "isPaid"), bool(order, "orderedDeliveryFromAddress"),
                bool(order, "orderedDeliveryToAddress"),
                code == ShipmentStatusCode.DELIVERED ? instant(order.get("orderDates"), "finish") : null,
                documentsOf(order), packages, payerOf(order.path("payer")));
    }

    /** Плательщик заказа: order.payer {name, inn}; нет или не объект — null. */
    private static List<ShipmentPayer> payerOf(JsonNode payer) {
        String name = text(payer, "name");
        String inn = text(payer, "inn");
        return payer.isObject() && (name != null || inn != null) ? List.of(new ShipmentPayer(name, inn, null)) : null;
    }

    /** Накладные заказа (documents[type=shipping]) × доступные режимы печати; код уникален внутри заказа. */
    private static List<ShipmentDocument> documentsOf(JsonNode order) {
        JsonNode documents = order.get("documents");
        if (documents == null || !documents.isArray()) {
            return null;
        }
        List<ShipmentDocument> result = new ArrayList<>();
        for (JsonNode document : documents) {
            String uid = text(document, "uid");
            String id = text(document, "id");
            if (!SHIPPING_DOC_TYPE.equals(text(document, "type")) || uid == null || id == null) {
                continue;
            }
            for (JsonNode mode : document.path("availableDocs")) {
                String modeName = mode.asText();
                result.add(new ShipmentDocument(modeName + "-" + id, DOC_NAMES.getOrDefault(modeName, modeName) + " " + id,
                        uid + DOC_REF_SEPARATOR + modeName));
            }
        }
        return result;
    }

    private static JournalItem toJournalItem(String waybill, JsonNode order) {
        String state = text(order, "state");
        return new JournalItem(waybill,
                text(order, "senderName", "sender"), firstOf(text(order, "senderInn", "derivalInn"), text(order.get("sender"), "inn")),
                text(order, "receiverName", "receiver"), firstOf(text(order, "receiverInn", "arrivalInn"), text(order.get("receiver"), "inn")),
                cityOf(order, "derival"), cityOf(order, "arrival"),
                firstOf(instant(order.get("orderDates"), "derivalFromOspSender"), instant(order, "orderedAt")),
                firstOf(instant(order.get("orderDates"), "arrivalToOspReceiver"), instant(order.get("orderDates"), "giveoutFromOspReceiver")),
                text(order, "orderNumber"),
                state == null ? null : toStatus(order), toCargo(order, normalizeStatus(state)));
    }

    private static String cityOf(JsonNode order, String side) {
        return firstOf(text(order, side + "City"), text(order.get(side), "city"));
    }

    private static <T> T firstOf(T first, T second) {
        return first != null ? first : second;
    }

    private static void register(ShipmentStatusCode code, String... states) {
        for (String state : states) {
            STATUS_CODES.put(state, code);
        }
    }

    @Override
    public CarrierCode code() {
        return CarrierCode.DELLIN;
    }

    @Override
    public boolean isConfigured() {
        return StringUtils.hasText(appKey);
    }

    @Override
    public Optional<List<TerminalData>> fetchTerminals() {
        JsonNode meta = http.post().uri(TERMINALS_PATH).body(Map.of("appkey", appKey)).retrieve().body(JsonNode.class);
        String hash = text(meta, "hash");
        String url = text(meta, "url");
        if (hash == null || url == null) {
            throw new CarrierApiException("Деловые Линии не вернули hash и url справочника терминалов");
        }
        if (hash.equals(lastHash)) {
            return Optional.empty();
        }
        JsonNode root = http.get().uri(URI.create(url)).retrieve().body(JsonNode.class);
        JsonNode cities = root == null ? null : root.get("city");
        if (cities == null || !cities.isArray()) {
            throw new CarrierApiException("Файл терминалов Деловых Линий не содержит списка городов");
        }
        List<TerminalData> result = new ArrayList<>();
        for (JsonNode city : cities) {
            String cityName = text(city, "name");
            for (JsonNode terminal : terminalsOf(city)) {
                // терминал, где груз не выдают, для доставки «до терминала» не годится
                if (terminal.path("giveoutCargo").asBoolean(true)) {
                    result.add(new TerminalData(text(terminal, "id"), PointType.TERMINAL, cityName,
                            text(terminal, "name"), text(terminal, "fullAddress", "address"),
                            number(terminal, "latitude"), number(terminal, "longitude"),
                            workingHours(terminal), text(terminal, "mainPhone")));
                }
            }
        }
        lastHash = hash;
        return Optional.of(result);
    }

    @Override
    public void forgetFetchState() {
        lastHash = null;
    }

    /** Терминалы города: массив в terminals или в terminals.terminal (разные версии файла). */
    private static Iterable<JsonNode> terminalsOf(JsonNode city) {
        JsonNode terminals = city.get("terminals");
        if (terminals != null && terminals.isObject()) {
            terminals = terminals.get("terminal");
        }
        return terminals != null && terminals.isArray() ? terminals : List.of();
    }

    private static JsonNode generalOrFirst(JsonNode tables) {
        for (JsonNode candidate : tables) {
            if (GENERAL_DEPARTMENT.equals(text(candidate, "department"))) {
                return candidate;
            }
        }
        return tables.get(0);
    }

    private static String workingHours(JsonNode terminal) {
        JsonNode tables = terminal.get("worktables");
        if (tables != null && tables.isObject()) {
            tables = tables.get("worktable");
        }
        if (tables == null || !tables.isArray() || tables.isEmpty()) {
            return null;
        }
        JsonNode table = generalOrFirst(tables);
        String timetable = text(table, "timetable");
        if (timetable != null) {
            return timetable;
        }
        List<String> days = new ArrayList<>();
        DAYS.forEach((field, label) -> {
            String hours = text(table, field);
            if (hours != null) {
                days.add(label + " " + hours);
            }
        });
        return days.isEmpty() ? null : String.join("; ", days);
    }
}
