package ru.rfsnab.orderservice.delivery;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import static ru.rfsnab.orderservice.delivery.CarrierJson.array;
import static ru.rfsnab.orderservice.delivery.CarrierJson.instant;
import static ru.rfsnab.orderservice.delivery.CarrierJson.number;
import static ru.rfsnab.orderservice.delivery.CarrierJson.text;

/**
 * Возовоз: API 2.5 (github.com/vozovoz/apiv2), объект terminal, действие get, токен в GET-параметре token.
 * Список читается страницами limit/offset. Заказы — объект order, действие get (статус и журнал).
 */
@Slf4j
@Component
public class VozovozClient implements CarrierClient {

    private static final String BASE_URL = "https://vozovoz.ru";
    private static final String API_PATH = "/api/?token={token}";
    private static final int PAGE_SIZE = 1000;
    private static final int MAX_PAGES = 500;
    private static final String CANCELLED_MARK = "отмен";

    private final RestClient http;
    private final String token;

    /** Создаёт клиент; без токена клиент отключён. */
    public VozovozClient(RestClient.Builder builder, @Value("${carriers.vozovoz.token:}") String token) {
        this.http = builder.baseUrl(BASE_URL).build();
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
                        text(dispatch.path("customer"), "companyName", "name"), text(dispatch.path("customer"), "inn"),
                        text(destination.path("customer"), "companyName", "name"), text(destination.path("customer"), "inn"),
                        cityOf(dispatch), cityOf(destination),
                        instant(dates, "shipped", "taken", "created", "create"), instant(dates, "arrived"),
                        text(order, "customId"), toStatus(order)));
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
     * Код статуса — по датам этапов (dates.given/arrived/taken/shipped/created) и слову «отмен» в тексте статуса:
     * перечень кодов статусов Возовоз в документации не приведён. status — строка или объект {code, message}.
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
        } else if (text(dates, "shipped", "taken", "created", "create") != null) {
            code = ShipmentStatusCode.IN_TRANSIT;
        }
        if (code == null) {
            log.warn("Возовоз: статус накладной не определён, код не меняется: {}", raw);
        }
        return new CarrierShipmentStatus(code, raw, cityOf(order.path("gateway").path("dispatch")),
                cityOf(order.path("gateway").path("destination")));
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
