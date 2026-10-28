package ru.rfsnab.orderservice.delivery.client;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import ru.rfsnab.orderservice.delivery.exception.CarrierApiException;
import ru.rfsnab.orderservice.delivery.models.entity.ShipmentPackage;
import ru.rfsnab.orderservice.delivery.models.entity.ShipmentPayer;
import ru.rfsnab.orderservice.delivery.models.entity.enums.CarrierCode;
import ru.rfsnab.orderservice.delivery.models.entity.enums.PointType;
import ru.rfsnab.orderservice.delivery.models.entity.enums.ShipmentStatusCode;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

import static ru.rfsnab.orderservice.delivery.client.CarrierJson.array;
import static ru.rfsnab.orderservice.delivery.client.CarrierJson.decimal;
import static ru.rfsnab.orderservice.delivery.client.CarrierJson.instant;
import static ru.rfsnab.orderservice.delivery.client.CarrierJson.integer;
import static ru.rfsnab.orderservice.delivery.client.CarrierJson.text;

/**
 * Байкал Сервис: REST API v2 (хост из carriers.baikal.base-url), ключ API передаётся как Basic-логин с пустым паролем.
 */
@Slf4j
@Component
public class BaikalClient implements CarrierClient {

    private static final String FIELD_NUMBER = "number";
    private static final String FIELD_CONSIGNEE = "consignee";
    private static final String FIELD_CARGO_LIST = "cargoList";
    private static final String FIELD_WEIGHT = "weight";
    private static final String FIELD_VOLUME = "volume";
    private static final String FIELD_DEPARTURE = "departure";
    private static final String FIELD_STATUS = "status";
    private static final String FIELD_DESTINATION = "destination";
    private static final String FIELD_TOTAL = "total";
    private static final String FIELD_CONSIGNOR = "consignor";
    private static final String FIELD_ADDRESS = "address";

    private static final String AFFILIATE_PATH = "/v2/affiliate";
    private static final String TRACKING_PATH = "/v1/tracking?number={number}";
    private static final String ORDER_LIST_PATH = "/v2/order/list";
    private static final String ORDER_DETAIL_PATH = "/v2/order/detail";
    private static final String REQUEST_LIST_PATH = "/v2/order/request";
    private static final int JOURNAL_PAGE_SIZE = 50;
    private static final int REQUEST_PAGE_SIZE = 100;
    /** Заявка «Не обработана» старше этого срока считается брошенной. */
    private static final Duration STALE_REQUEST_AGE = Duration.ofDays(14);
    /** Заявка в статусе «Выполнена» стала грузом (квитанцией). */
    private static final String REQUEST_DONE_MARK = "выполн";
    private static final DateTimeFormatter API_DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
    private static final String COORDINATES_SEPARATOR = ",";
    private static final int COORDINATES_PARTS = 2;
    private static final int LATITUDE_INDEX = 0;
    private static final int LONGITUDE_INDEX = 1;

    private final RestClient http;
    private final String baseUrl;
    private final String apiKey;

    /** Создаёт клиент; без apiKey клиент отключён. */
    public BaikalClient(RestClient.Builder builder,
                        @Value("${carriers.baikal.base-url:}") String baseUrl,
                        @Value("${carriers.baikal.api-key:}") String apiKey) {
        this.http = builder.baseUrl(baseUrl).build();
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
    }

    @Override
    public CarrierCode code() {
        return CarrierCode.BAIKAL;
    }

    @Override
    public boolean isConfigured() {
        return StringUtils.hasText(apiKey);
    }

    /** Терминалы филиалов; город — название филиала, терминалы, на которые невозможно прибытие грузов (finish=0), пропускаются. */
    @Override
    public Optional<List<TerminalData>> fetchTerminals() {
        JsonNode body = http().get().uri(AFFILIATE_PATH)
                .headers(h -> h.setBasicAuth(apiKey, ""))
                .retrieve()
                .body(JsonNode.class);
        List<TerminalData> result = new ArrayList<>();
        for (JsonNode affiliate : array(body)) {
            String city = text(affiliate, "name");
            for (JsonNode terminal : affiliate.path("terminals")) {
                if (terminal.path("finish").asInt(1) != 0) {
                    String map = text(terminal, "map");
                    result.add(new TerminalData(text(terminal, "id"), PointType.TERMINAL, city, text(terminal, "name"),
                            text(terminal, FIELD_ADDRESS), coordinate(map, LATITUDE_INDEX), coordinate(map, LONGITUDE_INDEX),
                            null, text(terminal, "phone")));
                }
            }
        }
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

    // ponytail: /v1/tracking принимает номер расписки/груза/заявки по документации; сквозной trackingnumber (наш waybill) этим
    // методом не подтверждён — сверить на реальном ответе, иначе опрос и ручное внесение будут получать {"error"}/404.
    /** Статус по одному запросу на накладную; ответ {"error"} — Байкал Сервис накладную не знает, её нет в карте. 404 — ошибка. */
    @Override
    public Map<String, CarrierShipmentStatus> fetchStatuses(List<String> waybills) {
        if (waybills.isEmpty()) {
            return Map.of();
        }
        Map<String, CarrierShipmentStatus> result = new HashMap<>();
        for (String waybill : waybills) {
            JsonNode body;
            try {
                body = http().get().uri(TRACKING_PATH, waybill)
                        .headers(h -> h.setBasicAuth(apiKey, ""))
                        .retrieve()
                        .body(JsonNode.class);
            } catch (HttpClientErrorException.NotFound e) {
                throw new CarrierApiException("Байкал Сервис ответил 404 на запрос статуса накладной");
            }
            if (body == null || !body.isObject()) {
                throw new CarrierApiException("Ответ Байкал Сервис не содержит статуса накладной");
            }
            if (body.has("error")) {
                continue;
            }
            // id статусов трекинга, заявок и квитанций нумеруются по-разному и расходятся с документацией — только по тексту
            String statusText = text(body.path(FIELD_STATUS), "text");
            if (statusText == null) {
                throw new CarrierApiException("Ответ Байкал Сервис не содержит статуса накладной");
            }
            result.put(waybill, toStatus(statusText, text(body, FIELD_DEPARTURE), text(body, FIELD_DESTINATION), trackingCargo(body)));
        }
        return result;
    }

    /**
     * Журнал: единственный номер перевозки — сквозной trackingnumber («Перевозка» в ЛК Байкал Сервис), общий у заявки
     * и её квитанции, поэтому они сходятся в одну запись. order/list постранично за период since..сейчас, по каждой
     * квитанции order/detail (ИНН сторон, грузы) — одна запись на квитанцию. Затем заявки order/request: «Выполнена»
     * пропускается (её квитанция несёт тот же номер), остальные — по записи. Сбой списка заявок не обрывает квитанции.
     */
    @Override
    public List<JournalItem> fetchJournal(Instant since) {
        Map<String, Object> period = Map.of("from", API_DATE_TIME.format(since.atOffset(ZoneOffset.UTC)),
                "to", API_DATE_TIME.format(Instant.now().atOffset(ZoneOffset.UTC)));
        List<JournalItem> requestItems = new ArrayList<>();
        try {
            addRequests(requestItems, period);
        } catch (CarrierApiException | RestClientException e) {
            log.warn("Байкал: список заявок пропущен: {}", e.getMessage());
            requestItems.clear();
        }
        // заявки первыми: квитанция с тем же trackingnumber записывается позже и её статус побеждает
        List<JournalItem> result = new ArrayList<>(requestItems);
        forEachListed(ORDER_LIST_PATH, "orderList", JOURNAL_PAGE_SIZE, period, order -> {
            String number = text(order, FIELD_NUMBER);
            String waybill = text(order, "trackingnumber");
            if (number == null || waybill == null) {
                log.warn("Байкал: квитанция {} без trackingnumber пропущена", number);
                return;
            }
            try {
                result.add(toReceipt(waybill, post(ORDER_DETAIL_PATH, Map.of(FIELD_NUMBER, number))));
            } catch (CarrierApiException | RestClientException e) {
                // одна сбойная квитанция не должна обрывать весь журнал
                log.warn("Байкал: квитанция {} пропущена: {}", number, e.getMessage());
            }
        });
        return result;
    }

    /** Постраничный обход списка (order/list, order/request): страницы до пустой или до recordCount. */
    private void forEachListed(String path, String listKey, int pageSize, Map<String, Object> period, Consumer<JsonNode> action) {
        int read = 0;
        for (int page = 0; ; page++) {
            JsonNode list = post(path, Map.of("date", period, "navigation", Map.of("page", page, "size", pageSize)));
            if (list == null) {
                throw new CarrierApiException("Байкал Сервис вернул пустой ответ на " + path);
            }
            JsonNode items = array(list, listKey);
            items.forEach(action);
            read += items.size();
            if (items.isEmpty() || read >= list.path("recordCount").asInt(0)) {
                return;
            }
        }
    }

    /** Заявки за период, кроме выполненных; заявка без trackingnumber пропускается. */
    private void addRequests(List<JournalItem> items, Map<String, Object> period) {
        List<JsonNode> requests = new ArrayList<>();
        forEachListed(REQUEST_LIST_PATH, "requestList", REQUEST_PAGE_SIZE, period, requests::add);
        for (JsonNode request : requests) {
            String waybill = text(request, "trackingnumber");
            String statusName = text(request.path(FIELD_STATUS), "name");
            if (waybill == null || (statusName != null && statusName.toLowerCase(Locale.ROOT).contains(REQUEST_DONE_MARK))) {
                continue;
            }
            JsonNode cargo = request.path(FIELD_CARGO_LIST).path(0);
            String from = text(request, "from");
            String to = text(request, "to");
            Instant created = instant(request, "date");
            CarrierShipmentStatus status = statusName == null ? null : toStatus(statusName, from, to, null);
            if (status != null && status.code() == ShipmentStatusCode.CREATED && created != null
                    && created.isBefore(Instant.now().minus(STALE_REQUEST_AGE))) {
                // брошенная заявка — ТК её не отклонила, но и не выполнила
                status = new CarrierShipmentStatus(ShipmentStatusCode.CANCELLED, statusName, from, to, null);
            }
            items.add(new JournalItem(waybill,
                    text(cargo.path(FIELD_CONSIGNOR), "name"), text(cargo.path(FIELD_CONSIGNOR).path("legalNaturalPerson"), "firstField"),
                    text(cargo.path(FIELD_CONSIGNEE), "name"), text(cargo.path(FIELD_CONSIGNEE).path("legalNaturalPerson"), "firstField"),
                    from, to, null, null, text(cargo, "referenceNumber"), status,
                    new ShipmentCargo(decimal(request, FIELD_WEIGHT), decimal(request, FIELD_VOLUME), integer(request, "cargocount"),
                            null, null, null, null, null, null, null, null)));
        }
    }

    /**
     * Квитанция одной записью. Стороны, маршрут и адреса — по первому грузу; вес, объём и места — сумма по грузам
     * (null игнорируются); номера грузов — в packages. Статус — самый ранний из известных по грузам
     * (порядок {@link ShipmentStatusCode}): запись не станет DELIVERED, пока не выдан каждый груз.
     */
    private static JournalItem toReceipt(String waybill, JsonNode detail) {
        JsonNode first = detail.path(FIELD_CARGO_LIST).path(0);
        String from = text(first.path(FIELD_DEPARTURE), "name");
        String to = text(first.path(FIELD_DESTINATION), "name");
        BigDecimal weight = null;
        BigDecimal volume = null;
        Integer places = null;
        List<ShipmentPackage> packages = new ArrayList<>();
        // плательщики услуг по всем грузам; ключ — ИНН, а без него название
        Map<String, ShipmentPayer> payers = new LinkedHashMap<>();
        CarrierShipmentStatus status = null;
        for (JsonNode cargo : detail.path(FIELD_CARGO_LIST)) {
            JsonNode goods = cargo.path("cargo");
            weight = sum(weight, decimal(goods, FIELD_WEIGHT));
            volume = sum(volume, decimal(goods, FIELD_VOLUME));
            Integer cargoPlaces = integer(goods, "places");
            if (cargoPlaces != null) {
                places = places == null ? cargoPlaces : places + cargoPlaces;
            }
            String number = text(cargo, FIELD_NUMBER);
            if (number != null) {
                packages.add(new ShipmentPackage(number, decimal(goods, FIELD_WEIGHT), null, null, null));
            }
            for (JsonNode service : cargo.path("services")) {
                String payerName = text(service.path("payer"), "name");
                String payerInn = text(service.path("payer"), "inn");
                if (payerName != null || payerInn != null) {
                    payers.putIfAbsent(payerInn != null ? payerInn : payerName, new ShipmentPayer(payerName, payerInn, null));
                }
            }
            String statusName = text(cargo.path(FIELD_STATUS), "name");
            if (statusName != null) {
                CarrierShipmentStatus s = toStatus(statusName, from, to, null);
                if (status == null || (s.code() != null && (status.code() == null || s.code().ordinal() < status.code().ordinal()))) {
                    status = s;
                }
            }
        }
        ShipmentCargo base = toCargo(detail, first);
        boolean delivered = status != null && status.code() == ShipmentStatusCode.DELIVERED;
        return new JournalItem(waybill,
                text(first.path(FIELD_CONSIGNOR), "name"), text(first.path(FIELD_CONSIGNOR), "inn"),
                text(first.path(FIELD_CONSIGNEE), "name"), text(first.path(FIELD_CONSIGNEE), "inn"),
                from, to, instant(detail, "date"), instant(first, "dateArrivalPlane"), text(first, "referenceNumber"), status,
                new ShipmentCargo(weight, volume, places, base.cost(), base.paid(), base.fromAddress(), base.toAddress(),
                        delivered ? base.deliveredAt() : null, null, packages.isEmpty() ? null : packages,
                        payers.isEmpty() ? null : List.copyOf(payers.values())));
    }

    private static BigDecimal sum(BigDecimal total, BigDecimal value) {
        if (value == null) {
            return total;
        }
        return total == null ? value : total.add(value);
    }

    /** Из трекинга известна только сумма (sum); без неё груза нет. */
    private static ShipmentCargo trackingCargo(JsonNode body) {
        BigDecimal sum = decimal(body, "sum");
        return sum == null ? null
                : new ShipmentCargo(null, null, null, sum, null, null, null, null, null, null, null);
    }

    /**
     * Груз из квитанции order/detail: вес/объём/места — cargo, сумма и оплата — total (у груза, иначе у квитанции).
     * Адрес: забор — departure.pickup.address; доставка — destination.delivery/pickup с адресом, иначе терминал.
     * Документов и мест (packages) в API нет.
     */
    private static ShipmentCargo toCargo(JsonNode detail, JsonNode item) {
        JsonNode goods = item.path("cargo");
        JsonNode total = item.has(FIELD_TOTAL) ? item.path(FIELD_TOTAL) : detail.path(FIELD_TOTAL);
        BigDecimal sum = decimal(total, "sum");
        BigDecimal paid = decimal(total, "paid");
        JsonNode destination = item.path(FIELD_DESTINATION);
        Boolean toAddress = null;
        if (text(destination.path("delivery"), FIELD_ADDRESS) != null || text(destination.path("pickup"), FIELD_ADDRESS) != null) {
            toAddress = Boolean.TRUE;
        } else if (text(destination.path("terminal"), "name") != null) {
            toAddress = Boolean.FALSE;
        }
        return new ShipmentCargo(decimal(goods, FIELD_WEIGHT), decimal(goods, FIELD_VOLUME), integer(goods, "places"), sum,
                sum == null || paid == null ? null : paid.compareTo(sum) >= 0,
                item.has(FIELD_DEPARTURE) ? text(item.path(FIELD_DEPARTURE).path("pickup"), FIELD_ADDRESS) != null : null,
                toAddress, instant(item, "dateIssued"), null, null, null);
    }

    private JsonNode post(String path, Map<String, Object> body) {
        JsonNode response = http().post().uri(path)
                .headers(h -> h.setBasicAuth(apiKey, ""))
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(JsonNode.class);
        if (response != null && (response.has("error") || response.has("errors"))) {
            throw new CarrierApiException("Байкал Сервис вернул ошибку на " + path);
        }
        return response;
    }

    /** Без BAIKAL_BASE_URL падает только обращение к Байкалу (импорт, синхронизация, трекинг), а не весь сервис. */
    private RestClient http() {
        if (!StringUtils.hasText(baseUrl)) {
            throw new CarrierApiException("Не задан адрес API Байкал Сервис (BAIKAL_BASE_URL)");
        }
        return http;
    }

    /** Координата из строки "широта,долгота"; null, если строки нет или она не разобралась (отсечёт валидация синхронизации). */
    private static Double coordinate(String map, int index) {
        if (map == null) {
            return null;
        }
        String[] parts = map.split(COORDINATES_SEPARATOR);
        if (parts.length != COORDINATES_PARTS) {
            return null;
        }
        try {
            return Double.parseDouble(parts[index].trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Код по ключевым словам названия статуса (квитанции, трекинга или заявки): id в API Байкала нумеруются по-разному
     * и расходятся с документацией, поэтому только имя; неизвестное — null.
     */
    static ShipmentStatusCode normalizeStatus(String status) {
        if (status == null) {
            return null;
        }
        String s = status.toLowerCase(Locale.ROOT);
        if (s.contains("не выдан") || s.contains("не доставлен")) {
            return null;
        }
        if (s.contains("отклон") || s.contains("отмен") || s.contains("возврат")) {
            return ShipmentStatusCode.CANCELLED;
        }
        if (s.contains("не обработан") || s.contains("подтвержден")) {
            return ShipmentStatusCode.CREATED;
        }
        if (s.contains("выдан") || s.contains("доставлен")) {
            return ShipmentStatusCode.DELIVERED;
        }
        if (s.contains("прибы")) {
            return ShipmentStatusCode.ARRIVED_AT_TERMINAL;
        }
        if (s.contains("в пути") || s.contains("принят") || s.contains("оформлен") || s.contains("отправлен")
                || s.contains("передан")) {
            return ShipmentStatusCode.IN_TRANSIT;
        }
        return null;
    }

    private static CarrierShipmentStatus toStatus(String raw, String from, String to, ShipmentCargo cargo) {
        ShipmentStatusCode code = normalizeStatus(raw);
        if (code == null) {
            log.warn("Байкал Сервис: неизвестный статус накладной, код не меняется: {}", raw);
        }
        return new CarrierShipmentStatus(code, raw, from, to, cargo);
    }
}
