package ru.rfsnab.orderservice.delivery;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.StreamSupport;

import static ru.rfsnab.orderservice.delivery.CarrierJson.array;
import static ru.rfsnab.orderservice.delivery.CarrierJson.number;
import static ru.rfsnab.orderservice.delivery.CarrierJson.text;

/**
 * СДЭК: OAuth + /v2/deliverypoints. ПВЗ и постаматы не фильтруются — оба типа идут в справочник.
 */
@Slf4j
@Component
public class CdekClient implements CarrierClient {

    private static final String BASE_URL = "https://api.cdek.ru/v2";
    private static final int PAGE_SIZE = 1000;
    private static final int MAX_PAGES = 200;
    private static final String TOTAL_PAGES_HEADER = "x-total-pages";
    private static final String POSTAMAT_TYPE = "POSTAMAT";

    /** Таблица сопоставления кодов статусов СДЭК → ShipmentStatusCode (по документации СДЭК; сверить на реальных ответах). */
    private static final Map<String, ShipmentStatusCode> STATUS_CODES = new HashMap<>();

    static {
        register(ShipmentStatusCode.IN_TRANSIT, "CREATED", "ACCEPTED", "RECEIVED_AT_SHIPMENT_WAREHOUSE",
                "READY_FOR_SHIPMENT_IN_SENDER_CITY", "TAKEN_BY_TRANSPORTER_FROM_SENDER_CITY", "SENT_TO_TRANSIT_CITY",
                "ACCEPTED_IN_TRANSIT_CITY", "ACCEPTED_AT_TRANSIT_WAREHOUSE", "READY_TO_SHIP_IN_TRANSIT_CITY",
                "READY_FOR_SHIPMENT_IN_TRANSIT_CITY", "TAKEN_BY_TRANSPORTER_FROM_TRANSIT_CITY", "SENT_TO_RECIPIENT_CITY",
                "ACCEPTED_IN_SENDER_CITY", "ACCEPTED_IN_RECIPIENT_CITY", "READY_FOR_SHIPMENT_IN_RECIPIENT_CITY",
                "TAKEN_BY_COURIER", "SHIPPED_TO_DESTINATION", "PASSED_TO_TRANSIT_CARRIER", "IN_CUSTOMS_INTERNATIONAL",
                "IN_CUSTOMS_LOCAL", "CUSTOMS_COMPLETE");
        register(ShipmentStatusCode.ARRIVED_AT_TERMINAL, "ACCEPTED_AT_RECIPIENT_CITY_WAREHOUSE", "ACCEPTED_AT_PICK_UP_POINT",
                "POSTAMAT_POSTED");
        register(ShipmentStatusCode.DELIVERED, "DELIVERED", "POSTAMAT_RECEIVED");
        register(ShipmentStatusCode.CANCELLED, "REMOVED");
        register(ShipmentStatusCode.PROBLEM, "NOT_DELIVERED", "INVALID", "POSTAMAT_SEIZED",
                "RETURNED_TO_SENDER_CITY_WAREHOUSE", "RETURNED_TO_TRANSIT_WAREHOUSE", "RETURNED_TO_RECIPIENT_CITY_WAREHOUSE");
    }

    private final RestClient http;
    private final String clientId;
    private final String clientSecret;

    /** Создаёт клиент; без clientId и clientSecret клиент отключён. */
    public CdekClient(RestClient.Builder builder,
                      @Value("${carriers.cdek.client-id:}") String clientId,
                      @Value("${carriers.cdek.client-secret:}") String clientSecret) {
        this.http = builder.baseUrl(BASE_URL).build();
        this.clientId = clientId;
        this.clientSecret = clientSecret;
    }

    @Override
    public CarrierCode code() {
        return CarrierCode.CDEK;
    }

    @Override
    public boolean isConfigured() {
        return StringUtils.hasText(clientId) && StringUtils.hasText(clientSecret);
    }

    @Override
    public Optional<List<TerminalData>> fetchTerminals() {
        String bearer = "Bearer " + fetchToken();
        List<TerminalData> result = new ArrayList<>();
        int totalPages = 1;
        for (int page = 0; page < totalPages && page < MAX_PAGES; page++) {
            ResponseEntity<JsonNode> response = http.get()
                    .uri("/deliverypoints?country_code=RU&type=ALL&size={size}&page={page}", PAGE_SIZE, page)
                    .header(HttpHeaders.AUTHORIZATION, bearer)
                    .retrieve()
                    .toEntity(JsonNode.class);
            StreamSupport.stream(array(response.getBody()).spliterator(), false)
                    .map(CdekClient::toTerminal)
                    .forEach(result::add);
            totalPages = totalPages(response.getHeaders());
        }
        return Optional.of(result);
    }

    @Override
    public boolean supportsStatusTracking() {
        return isConfigured();
    }

    /**
     * Статусы по номерам СДЭК: один запрос /v2/orders?cdek_number= на накладную.
     * Накладная, которую СДЭК не знает (404, любая 4xx кроме авторизации и лимитов), в карту не попадает.
     */
    @Override
    public Map<String, CarrierShipmentStatus> fetchStatuses(List<String> waybills) {
        String bearer = "Bearer " + fetchToken();
        Map<String, CarrierShipmentStatus> result = new HashMap<>();
        for (String waybill : waybills) {
            fetchStatus(bearer, waybill).ifPresent(status -> result.put(waybill, status));
        }
        return result;
    }

    /** Нормализованный код по коду статуса СДЭК; null, если код не известен таблице. */
    static ShipmentStatusCode normalizeStatus(String cdekCode) {
        return cdekCode == null ? null : STATUS_CODES.get(cdekCode);
    }

    private Optional<CarrierShipmentStatus> fetchStatus(String bearer, String waybill) {
        JsonNode body;
        try {
            body = http.get().uri("/orders?cdek_number={number}", waybill)
                    .header(HttpHeaders.AUTHORIZATION, bearer)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (HttpClientErrorException e) {
            int status = e.getStatusCode().value();
            if (status == HttpStatus.UNAUTHORIZED.value() || status == HttpStatus.FORBIDDEN.value()
                    || status == HttpStatus.TOO_MANY_REQUESTS.value()) {
                throw e;
            }
            return Optional.empty();
        }
        JsonNode entity = body == null ? null : body.get("entity");
        if (entity == null || entity.isNull()) {
            return Optional.empty();
        }
        JsonNode latest = latestStatus(entity.get("statuses"));
        String code = text(latest, "code");
        ShipmentStatusCode normalized = normalizeStatus(code);
        if (code != null && normalized == null) {
            log.warn("СДЭК: неизвестный статус накладной, код не меняется: {}", code);
        }
        return Optional.of(new CarrierShipmentStatus(normalized, code == null ? null : firstNonNull(text(latest, "name"), code),
                text(entity.get("from_location"), "city"), text(entity.get("to_location"), "city")));
    }

    /** Последний по date_time статус; без дат — последний в массиве. */
    private static JsonNode latestStatus(JsonNode statuses) {
        if (statuses == null || !statuses.isArray() || statuses.isEmpty()) {
            return null;
        }
        JsonNode latest = statuses.get(statuses.size() - 1);
        String latestDate = text(latest, "date_time");
        for (JsonNode candidate : statuses) {
            String date = text(candidate, "date_time");
            if (date != null && (latestDate == null || date.compareTo(latestDate) > 0)) {
                latest = candidate;
                latestDate = date;
            }
        }
        return latest;
    }

    private static String firstNonNull(String first, String second) {
        return first != null ? first : second;
    }

    private static void register(ShipmentStatusCode code, String... cdekCodes) {
        for (String cdekCode : cdekCodes) {
            STATUS_CODES.put(cdekCode, code);
        }
    }

    private String fetchToken() {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);
        JsonNode body = http.post().uri("/oauth/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(JsonNode.class);
        String token = text(body, "access_token");
        if (token == null) {
            throw new CarrierApiException("СДЭК не выдал токен доступа");
        }
        return token;
    }

    private static int totalPages(HttpHeaders headers) {
        String value = headers.getFirst(TOTAL_PAGES_HEADER);
        try {
            return value == null ? 1 : Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            throw new CarrierApiException("СДЭК вернул некорректный " + TOTAL_PAGES_HEADER);
        }
    }

    private static TerminalData toTerminal(JsonNode item) {
        JsonNode location = item.get("location");
        PointType type = POSTAMAT_TYPE.equals(text(item, "type")) ? PointType.POSTAMAT : PointType.PVZ;
        List<String> phones = new ArrayList<>();
        JsonNode phoneNodes = item.get("phones");
        if (phoneNodes != null && phoneNodes.isArray()) {
            phoneNodes.forEach(p -> {
                String number = text(p, "number");
                if (number != null) {
                    phones.add(number);
                }
            });
        }
        return new TerminalData(text(item, "code"), type, text(location, "city"), text(item, "name"),
                text(location, "address_full", "address"), number(location, "latitude"), number(location, "longitude"),
                text(item, "work_time"), phones.isEmpty() ? null : String.join(", ", phones));
    }
}
