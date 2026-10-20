package ru.rfsnab.orderservice.delivery.client;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import ru.rfsnab.orderservice.delivery.exception.CarrierApiException;
import ru.rfsnab.orderservice.delivery.models.entity.ShipmentDocument;
import ru.rfsnab.orderservice.delivery.models.entity.ShipmentPayer;
import ru.rfsnab.orderservice.delivery.models.entity.enums.CarrierCode;
import ru.rfsnab.orderservice.delivery.models.entity.enums.PointType;
import ru.rfsnab.orderservice.delivery.models.entity.enums.ShipmentStatusCode;

import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import static ru.rfsnab.orderservice.delivery.client.CarrierJson.array;
import static ru.rfsnab.orderservice.delivery.client.CarrierJson.bool;
import static ru.rfsnab.orderservice.delivery.client.CarrierJson.decimal;
import static ru.rfsnab.orderservice.delivery.client.CarrierJson.instant;
import static ru.rfsnab.orderservice.delivery.client.CarrierJson.integer;
import static ru.rfsnab.orderservice.delivery.client.CarrierJson.number;
import static ru.rfsnab.orderservice.delivery.client.CarrierJson.text;

/**
 * Возовоз: API 2.5 (github.com/vozovoz/apiv2), объект terminal, действие get, токен в GET-параметре token.
 * Список читается страницами limit/offset. Заказы — объект order, действие get (статус и журнал).
 */
@Slf4j
@Component
public class VozovozClient implements CarrierClient {

    private static final String HTTPS_SCHEME = "https";
    /** Документы отдаются только с доменов Возовоза. */
    private static final String DOCUMENT_DOMAIN = "vozovoz.ru";
    private static final String API_PATH = "/api/?token={token}";
    private static final int PAGE_SIZE = 1000;
    private static final int MAX_PAGES = 500;
    private static final String CANCELLED_MARK = "отмен";
    /** XML для ЭДО менеджеру не нужен — в список документов не попадает. */
    private static final String EDO_XML_DOC_CODE = "etdmsKonturXml";
    private static final Map<String, String> DOC_NAMES = Map.of(
            "invoice", "Счёт", "utd", "УПД", "lrc", "Экспедиторская расписка", "markingList", "Маркировочный лист",
            "cmr", "CMR", "doc", "Бланк заказа", "cargoPhoto", "Фото груза");

    private final RestClient http;
    private final String token;

    /** Создаёт клиент; без токена клиент отключён. */
    public VozovozClient(RestClient.Builder builder,
                         @Value("${carriers.vozovoz.base-url}") String baseUrl,
                         @Value("${carriers.vozovoz.token:}") String token) {
        this.http = builder.baseUrl(baseUrl).build();
        this.token = token;
    }

    @Override
    public CarrierCode code() {
        return CarrierCode.VOZOVOZ;
    }

    @Override
    public boolean isConfigured() {
        return StringUtils.hasText(token);
    }

    @Override
    public Optional<List<TerminalData>> fetchTerminals() {
        List<TerminalData> result = new ArrayList<>();
        fetchAll("terminal", Map.of()).forEach(item -> result.add(toTerminal(item)));
        return Optional.of(result);
    }

    @Override
    public boolean supportsStatusTracking() {
        return isConfigured();
    }

    @Override
    public boolean supportsJournal() {
        return isConfigured();
    }

    /** Статусы по order.get с filters.number (номер, id или пользовательский номер); заказа нет в ответе — Возовоз его не знает. */
    @Override
    public Map<String, CarrierShipmentStatus> fetchStatuses(List<String> waybills) {
        if (waybills.isEmpty()) {
            return Map.of();
        }
        Map<String, CarrierShipmentStatus> result = new HashMap<>();
        for (JsonNode order : fetchAll("order", Map.of("filters", Map.of("number", waybills)))) {
            CarrierShipmentStatus status = toStatus(order);
            for (String key : new String[]{text(order, "number"), text(order, "id"), text(order, "customId")}) {
                if (key != null && waybills.contains(key)) {
                    result.put(key, status);
                }
            }
        }
        return result;
    }

    /** Журнал: order.get с фильтром по дате оформления от since до сегодня. */
    @Override
    public List<JournalItem> fetchJournal(Instant since) {
        Map<String, Object> created = Map.of("from", LocalDate.ofInstant(since, ZoneOffset.UTC).toString(),
                "to", LocalDate.now(ZoneOffset.UTC).toString());
        List<JournalItem> result = new ArrayList<>();
        for (JsonNode order : fetchAll("order", Map.of("filters", Map.of("dates", Map.of("created", created))))) {
            String waybill = text(order, "number");
            if (waybill != null) {
                JsonNode dispatch = order.path("gateway").path("dispatch");
                JsonNode destination = order.path("gateway").path("destination");
                JsonNode dates = order.path("dates");
                result.add(new JournalItem(waybill,
                        text(dispatch.path("customer"), "company", "companyName", "name"), text(dispatch.path("customer"), "inn"),
                        text(destination.path("customer"), "company", "companyName", "name"), text(destination.path("customer"), "inn"),
                        cityOf(dispatch), cityOf(destination),
                        instant(dates, "shipped", "taken", "created", "create"), instant(destination.path("point"), "date"),
                        text(order, "customId"), toStatus(order), toCargo(order)));
            }
        }
        return result;
    }

    /** Все элементы response.data объекта object, постранично; ошибка API или ответ без массива — CarrierApiException. */
    private List<JsonNode> fetchAll(String object, Map<String, Object> params) {
        List<JsonNode> result = new ArrayList<>();
        int offset = 0;
        for (int pageNo = 0; pageNo < MAX_PAGES; pageNo++) {
            Map<String, Object> pageParams = new HashMap<>(params);
            pageParams.put("limit", PAGE_SIZE);
            pageParams.put("offset", offset);
            JsonNode body;
            try {
                body = http.post().uri(API_PATH, token)
                        .body(Map.of("object", object, "action", "get", "params", pageParams))
                        .retrieve()
                        .body(JsonNode.class);
            } catch (RestClientException e) {
                // сообщение исключения содержит URL с токеном — наружу его не отдаём
                throw new CarrierApiException("Возовоз недоступен: " + e.getClass().getSimpleName());
            }
            String error = body == null ? null : text(body.path("error"), "message");
            if (error != null) {
                throw new CarrierApiException("Возовоз: " + error);
            }
            JsonNode page = array(body, "response", "data");
            page.forEach(result::add);
            // сервер сам урезает limit (отдаёт по 100), поэтому листаем по meta.total; нет total — до пустой страницы
            int total = body.path("response").path("meta").path("total").asInt(0);
            offset += page.size();
            if (page.isEmpty() || (total > 0 && offset >= total)) {
                return result;
            }
        }
        throw new CarrierApiException("Возовоз: слишком много страниц в ответе");
    }

    /**
     * Код статуса — по датам этапов (given → выдан, arrived → прибыл, taken/shipped → в пути, только create/created → оформлен)
     * и слову «отмен» в тексте статуса: перечень кодов статусов Возовоз в документации не приведён. status — строка или объект {code, message}.
     */
    private static CarrierShipmentStatus toStatus(JsonNode order) {
        JsonNode status = order.get("status");
        String raw = status != null && status.isObject() ? text(status, "message", "code") : text(order, "status");
        JsonNode dates = order.path("dates");
        ShipmentStatusCode code = null;
        if (raw != null && raw.toLowerCase(Locale.ROOT).contains(CANCELLED_MARK)) {
            code = ShipmentStatusCode.CANCELLED;
        } else if (text(dates, "given") != null) {
            code = ShipmentStatusCode.DELIVERED;
        } else if (text(dates, "arrived") != null) {
            code = ShipmentStatusCode.ARRIVED_AT_TERMINAL;
        } else if (text(dates, "taken", "shipped") != null) {
            code = ShipmentStatusCode.IN_TRANSIT;
        } else if (text(dates, "create", "created") != null) {
            code = ShipmentStatusCode.CREATED;
        }
        if (code == null) {
            log.warn("Возовоз: статус накладной не определён, код не меняется: {}", raw);
        }
        return new CarrierShipmentStatus(code, raw, cityOf(order.path("gateway").path("dispatch")),
                cityOf(order.path("gateway").path("destination")), toCargo(order));
    }

    /** Груз из order.get; isPaid в ответе есть, хотя в документации его нет. */
    private static ShipmentCargo toCargo(JsonNode order) {
        JsonNode dimension = order.path("cargo").path("dimension");
        JsonNode gateway = order.path("gateway");
        return new ShipmentCargo(decimal(dimension, "weight"), decimal(dimension, "volume"), integer(dimension, "quantity"),
                decimal(order, "price"), bool(order, "isPaid"), isAddress(gateway.path("dispatch").path("point")),
                isAddress(gateway.path("destination").path("point")), instant(order.path("dates"), "given"),
                documentsOf(order.get("documentList")), null, payerOf(order.path("payer")));
    }

    /** Плательщик: название — company/companyName; name в ответе — контактное лицо, берём его лишь в крайнем случае. */
    private static List<ShipmentPayer> payerOf(JsonNode payer) {
        String name = text(payer, "company", "companyName", "name");
        String inn = text(payer, "inn");
        return payer.isObject() && (name != null || inn != null) ? List.of(new ShipmentPayer(name, inn, null)) : null;
    }

    /** Есть терминал — false, иначе есть адрес — true; ни того ни другого — null. */
    private static Boolean isAddress(JsonNode point) {
        if (point.path("terminal").isObject()) {
            return Boolean.FALSE;
        }
        return text(point, "address") != null ? Boolean.TRUE : null;
    }

    /** documentList {код: url}; null, если блока нет. */
    private static List<ShipmentDocument> documentsOf(JsonNode documentList) {
        if (documentList == null || !documentList.isObject()) {
            return null;
        }
        List<ShipmentDocument> result = new ArrayList<>();
        documentList.fieldNames().forEachRemaining(code -> {
            String url = text(documentList, code);
            if (url != null && !EDO_XML_DOC_CODE.equals(code)) {
                result.add(new ShipmentDocument(code, DOC_NAMES.getOrDefault(code, code), url));
            }
        });
        return result;
    }

    /** Документ — прямая ссылка из documentList. */
    @Override
    public byte[] downloadDocument(ShipmentDocument doc) {
        URI uri = URI.create(doc.ref());
        String host = uri.getHost();
        if (!HTTPS_SCHEME.equalsIgnoreCase(uri.getScheme()) || host == null
                || !(host.equalsIgnoreCase(DOCUMENT_DOMAIN) || host.toLowerCase(Locale.ROOT).endsWith("." + DOCUMENT_DOMAIN))) {
            throw new CarrierApiException("Ссылка на документ Возовоза ведёт на чужой хост или не https");
        }
        byte[] body;
        try {
            body = http.get().uri(uri).retrieve().body(byte[].class);
        } catch (RestClientException e) {
            throw new CarrierApiException("Возовоз: не удалось скачать документ: " + e.getClass().getSimpleName());
        }
        if (body == null || body.length == 0) {
            throw new CarrierApiException("Возовоз вернул пустой документ");
        }
        return body;
    }

    private static String cityOf(JsonNode gatewaySide) {
        return text(gatewaySide.path("point").path("location"), "name");
    }

    private static TerminalData toTerminal(JsonNode item) {
        JsonNode coordinates = item.path("coordinates");
        return new TerminalData(text(item, "guid"), PointType.TERMINAL, text(item, "location_name"),
                text(item, "name"), text(item, "address"), number(coordinates, "latitude"),
                number(coordinates, "longitude"), null, null);
    }
}
