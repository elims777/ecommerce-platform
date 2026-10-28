package ru.rfsnab.orderservice.delivery.client;

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
import ru.rfsnab.orderservice.delivery.exception.CarrierApiException;
import ru.rfsnab.orderservice.delivery.models.entity.ShipmentDocument;
import ru.rfsnab.orderservice.delivery.models.entity.ShipmentPackage;
import ru.rfsnab.orderservice.delivery.models.entity.enums.CarrierCode;
import ru.rfsnab.orderservice.delivery.models.entity.enums.PointType;
import ru.rfsnab.orderservice.delivery.models.entity.enums.ShipmentStatusCode;

import java.math.BigDecimal;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.StreamSupport;

import static ru.rfsnab.orderservice.delivery.client.CarrierJson.array;
import static ru.rfsnab.orderservice.delivery.client.CarrierJson.decimal;
import static ru.rfsnab.orderservice.delivery.client.CarrierJson.instant;
import static ru.rfsnab.orderservice.delivery.client.CarrierJson.integer;
import static ru.rfsnab.orderservice.delivery.client.CarrierJson.number;
import static ru.rfsnab.orderservice.delivery.client.CarrierJson.text;

/**
 * СДЭК: OAuth + /v2/deliverypoints. ПВЗ и постаматы не фильтруются — оба типа идут в справочник.
 */
@Slf4j
@Component
public class CdekClient implements CarrierClient {

    private static final String BEARER_PREFIX = "Bearer ";

    private static final String HTTPS_SCHEME = "https";
    private static final int PAGE_SIZE = 1000;
    private static final int MAX_PAGES = 200;
    private static final String TOTAL_PAGES_HEADER = "x-total-pages";
    private static final String POSTAMAT_TYPE = "POSTAMAT";
    private static final BigDecimal GRAMS_IN_KG = BigDecimal.valueOf(1000);
    private static final BigDecimal CM3_IN_M3 = BigDecimal.valueOf(1_000_000);
    /** delivery_mode: 1 д-д, 2 д-с, 3 с-д, 4 с-с, 6 д-постамат, 7 с-постамат, 8 постамат-д, 9 постамат-с, 10 постамат-постамат. */
    private static final Set<Integer> KNOWN_DELIVERY_MODES = Set.of(1, 2, 3, 4, 6, 7, 8, 9, 10);
    private static final Set<Integer> FROM_ADDRESS_MODES = Set.of(1, 2, 6);
    private static final Set<Integer> TO_ADDRESS_MODES = Set.of(1, 3, 8);
    private static final Map<String, String> DOC_NAMES = Map.of("waybill", "Накладная", "barcode", "Штрихкоды");

    /** Таблица сопоставления кодов статусов СДЭК → ShipmentStatusCode (по документации apidoc.cdek.ru). */
    private static final Map<String, ShipmentStatusCode> STATUS_CODES = new HashMap<>();

    static {
        register(ShipmentStatusCode.CREATED, "ACCEPTED", "CREATED");
        register(ShipmentStatusCode.IN_TRANSIT, "RECEIVED_AT_SHIPMENT_WAREHOUSE", "READY_FOR_SHIPMENT_IN_SENDER_CITY",
                "TAKEN_BY_TRANSPORTER_FROM_SENDER_CITY", "SENT_TO_RECIPIENT_CITY", "ACCEPTED_IN_RECIPIENT_CITY",
                "ACCEPTED_AT_RECIPIENT_CITY_WAREHOUSE", "TAKEN_BY_COURIER", "ACCEPTED_AT_TRANSIT_WAREHOUSE",
                "READY_FOR_SHIPMENT_IN_TRANSIT_CITY", "TAKEN_BY_TRANSPORTER_FROM_TRANSIT_CITY", "SENT_TO_TRANSIT_CITY",
                "ACCEPTED_IN_TRANSIT_CITY", "SENT_TO_SENDER_CITY", "ACCEPTED_IN_SENDER_CITY", "ENTERED_TO_TRANSIT_WAREHOUSE",
                "ENTERED_TO_RECIPIENT_CITY_WAREHOUSE", "IN_CUSTOMS_INTERNATIONAL", "SHIPPED_TO_DESTINATION",
                "PASSED_TO_TRANSIT_CARRIER", "IN_CUSTOMS_LOCAL", "CUSTOMS_COMPLETE");
        register(ShipmentStatusCode.ARRIVED_AT_TERMINAL, "ACCEPTED_AT_PICK_UP_POINT", "ENTERED_TO_PICK_UP_POINT",
                "POSTOMAT_POSTED");
        register(ShipmentStatusCode.DELIVERED, "DELIVERED", "POSTOMAT_RECEIVED");
        register(ShipmentStatusCode.CANCELLED, "REMOVED", "NOT_DELIVERED");
        register(ShipmentStatusCode.PROBLEM, "POSTOMAT_SEIZED", "RETURNED_TO_SENDER_CITY_WAREHOUSE",
                "RETURNED_TO_TRANSIT_WAREHOUSE", "RETURNED_TO_RECIPIENT_CITY_WAREHOUSE");
    }

    private final RestClient http;
    private final String baseUrl;
    private final String clientId;
    private final String clientSecret;

    /** Создаёт клиент; без clientId и clientSecret клиент отключён. */
    public CdekClient(RestClient.Builder builder,
                      @Value("${carriers.cdek.base-url}") String baseUrl,
                      @Value("${carriers.cdek.client-id:}") String clientId,
                      @Value("${carriers.cdek.client-secret:}") String clientSecret) {
        this.http = builder.baseUrl(baseUrl).build();
        this.baseUrl = baseUrl;
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
        String bearer = BEARER_PREFIX + fetchToken();
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
        String bearer = BEARER_PREFIX + fetchToken();
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
                text(entity.get("from_location"), "city"), text(entity.get("to_location"), "city"),
                toCargo(entity, body.get("related_entities"))));
    }

    /** Скачивание документа: GET по url из related_entities с тем же Bearer-токеном. */
    @Override
    public byte[] downloadDocument(ShipmentDocument doc) {
        URI uri = URI.create(doc.ref());
        // токен СДЭК не отдаём чужому хосту, даже если такая ссылка пришла в ответе
        if (!HTTPS_SCHEME.equalsIgnoreCase(uri.getScheme()) || !URI.create(baseUrl).getHost().equalsIgnoreCase(uri.getHost())) {
            throw new CarrierApiException("Ссылка на документ СДЭК ведёт на чужой хост или не https");
        }
        byte[] body = http.get().uri(uri)
                .header(HttpHeaders.AUTHORIZATION, BEARER_PREFIX + fetchToken())
                .retrieve().body(byte[].class);
        if (body == null || body.length == 0) {
            throw new CarrierApiException("СДЭК вернул пустой документ");
        }
        return body;
    }

    /**
     * Груз из заказа: вес (граммы → кг), объём (Σ длина*ширина*высота, см → м3) и места считаются по packages;
     * оплата в API не отдаётся (paid = null).
     */
    private static ShipmentCargo toCargo(JsonNode entity, JsonNode relatedEntities) {
        JsonNode packagesNode = entity.get("packages");
        List<ShipmentPackage> packages = packagesNode == null || !packagesNode.isArray() ? null : new ArrayList<>();
        BigDecimal weightKg = null;
        BigDecimal volumeM3 = null;
        if (packages != null) {
            for (JsonNode node : packagesNode) {
                BigDecimal kg = toKg(decimal(node, "weight"));
                BigDecimal length = decimal(node, "length");
                BigDecimal width = decimal(node, "width");
                BigDecimal height = decimal(node, "height");
                packages.add(new ShipmentPackage(text(node, "number"), kg, length, width, height));
                if (kg != null) {
                    weightKg = weightKg == null ? kg : weightKg.add(kg);
                }
                if (length != null && width != null && height != null) {
                    BigDecimal volume = length.multiply(width).multiply(height).divide(CM3_IN_M3);
                    volumeM3 = volumeM3 == null ? volume : volumeM3.add(volume);
                }
            }
        }
        Integer mode = integer(entity, "delivery_mode");
        boolean known = mode != null && KNOWN_DELIVERY_MODES.contains(mode);
        JsonNode detail = entity.path("delivery_detail");
        return new ShipmentCargo(weightKg, volumeM3, packages == null ? null : packages.size(), decimal(detail, "total_sum"),
                null, known ? FROM_ADDRESS_MODES.contains(mode) : null, known ? TO_ADDRESS_MODES.contains(mode) : null,
                instant(detail, "date"), documentsOf(relatedEntities), packages, null);
    }

    private static BigDecimal toKg(BigDecimal grams) {
        return grams == null ? null : grams.divide(GRAMS_IN_KG);
    }

    /** Накладная и штрихкоды из related_entities (корень ответа) — только сформированные, с url; null, если блока нет. */
    private static List<ShipmentDocument> documentsOf(JsonNode relatedEntities) {
        if (relatedEntities == null || !relatedEntities.isArray()) {
            return null;
        }
        List<ShipmentDocument> result = new ArrayList<>();
        for (JsonNode entity : relatedEntities) {
            String type = text(entity, "type");
            String url = text(entity, "url");
            if (type != null && DOC_NAMES.containsKey(type) && url != null) {
                result.add(new ShipmentDocument(type, DOC_NAMES.get(type), url));
            }
        }
        return result;
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
