package ru.rfsnab.orderservice.delivery;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.StringJoiner;

import static ru.rfsnab.orderservice.delivery.CarrierJson.array;
import static ru.rfsnab.orderservice.delivery.CarrierJson.instant;
import static ru.rfsnab.orderservice.delivery.CarrierJson.number;
import static ru.rfsnab.orderservice.delivery.CarrierJson.text;

/**
 * ПЭК: POST kabinet.pecom.ru/api/v1/branches/all/, Basic-авторизация логином и ключом API.
 * Точки выдачи — склады branches[].divisions[].warehouses[], которые выдают груз и не закрыты.
 */
@Slf4j
@Component
public class PecClient implements CarrierClient {

    private static final String BASE_URL = "https://kabinet.pecom.ru";
    private static final String BRANCHES_PATH = "/api/v1/branches/all/";
    private static final String STATUS_PATH = "/api/v1/cargos/status/";
    private static final String LIST_PATH = "/api/v1/cargos/list/";
    private static final int STATUS_BATCH_SIZE = 15;
    private static final int NOT_FOUND_STATUS = 404;
    private static final String[] DAY_NAMES = {"Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс"};

    /** Тексты cargos[].info.cargoStatus из документации ПЭК (в нижнем регистре) → ShipmentStatusCode. */
    private static final Map<String, ShipmentStatusCode> STATUS_CODES = new HashMap<>();

    static {
        register(ShipmentStatusCode.IN_TRANSIT, "заявка на забор зарегистрирована", "ожидается передача груза от отправителя",
                "принят к перевозке", "принят на пвз", "оформлен", "в пути", "в пути на терминал", "выполняется адресная доставка");
        register(ShipmentStatusCode.ARRIVED_AT_TERMINAL, "прибыл", "прибыл частично", "разгружается. ожидайте оповещения");
        register(ShipmentStatusCode.DELIVERED, "выдан получателю", "доставлен получателю", "выдан");
        register(ShipmentStatusCode.CANCELLED, "аннулировано до приемки груза", "возвращен отправителю", "отправлен на возврат");
        register(ShipmentStatusCode.PROBLEM, "утилизирован", "изъят на таможне");
    }

    private final RestClient http;
    private final String login;
    private final String apiKey;

    /** Создаёт клиент; без login и apiKey клиент отключён. */
    public PecClient(RestClient.Builder builder,
                     @Value("${carriers.pec.login:}") String login,
                     @Value("${carriers.pec.api-key:}") String apiKey) {
        this.http = builder.baseUrl(BASE_URL).build();
        this.login = login;
        this.apiKey = apiKey;
    }

    @Override
    public CarrierCode code() {
        return CarrierCode.PEC;
    }

    @Override
    public boolean isConfigured() {
        return StringUtils.hasText(login) && StringUtils.hasText(apiKey);
    }

    @Override
    public Optional<List<TerminalData>> fetchTerminals() {
        JsonNode body = http.post().uri(BRANCHES_PATH)
                .headers(h -> h.setBasicAuth(login, apiKey))
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of())
                .retrieve()
                .body(JsonNode.class);
        List<TerminalData> result = new ArrayList<>();
        for (JsonNode branch : array(body, "branches")) {
            for (JsonNode division : branch.path("divisions")) {
                String city = cityOf(branch, text(division, "cityId"));
                for (JsonNode warehouse : division.path("warehouses")) {
                    if (warehouse.path("isWarehouseGivesFreights").asBoolean(false)
                            && warehouse.path("departmentClosingDate").isNull()) {
                        result.add(toTerminal(warehouse, city));
                    }
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

    /** Статусы: POST /cargos/status/ пачками по {@value #STATUS_BATCH_SIZE} кодов; груза нет в ответе — ПЭК его не знает. */
    @Override
    public Map<String, CarrierShipmentStatus> fetchStatuses(List<String> waybills) {
        if (waybills.isEmpty()) {
            return Map.of();
        }
        Map<String, CarrierShipmentStatus> result = new HashMap<>();
        for (int from = 0; from < waybills.size(); from += STATUS_BATCH_SIZE) {
            List<String> batch = waybills.subList(from, Math.min(from + STATUS_BATCH_SIZE, waybills.size()));
            JsonNode body = post(STATUS_PATH, Map.of("cargoCodes", batch));
            if (isNotFoundError(body)) {
                continue;
            }
            for (JsonNode cargo : cargosOf(body)) {
                String code = text(cargo.path("cargo"), "code");
                if (code != null && waybills.contains(code)) {
                    result.put(code, toStatus(cargo));
                }
            }
        }
        return result;
    }

    /**
     * Журнал: POST /cargos/list/ с dateBegin/dateEnd (поля подтверждены ответом валидации API).
     * ponytail: формат ответа списка в публичной документации не описан — предполагаем cargos[] в формате /cargos/status/.
     */
    @Override
    public List<JournalItem> fetchJournal(Instant since) {
        JsonNode body = post(LIST_PATH, Map.of("dateBegin", LocalDate.ofInstant(since, ZoneOffset.UTC).toString(),
                "dateEnd", LocalDate.now(ZoneOffset.UTC).toString()));
        List<JournalItem> result = new ArrayList<>();
        for (JsonNode cargo : cargosOf(body)) {
            String code = text(cargo.path("cargo"), "code");
            if (code != null) {
                JsonNode info = cargo.path("info");
                result.add(new JournalItem(code,
                        text(cargo.path("sender"), "sender"), null, text(cargo.path("receiver"), "receiver"), null,
                        cityFrom(cargo), cityTo(cargo),
                        instant(info, "sendingDateTime", "takeOnStockDateTime"),
                        instant(info, "arrivalPlanDateTime", "deliveryPlanDate"),
                        text(cargo.path("cargo"), "orderNumber"),
                        text(info, "cargoStatus") == null ? null : toStatus(cargo)));
            }
        }
        return result;
    }

    /** Нормализованный код по тексту статуса груза ПЭК (без хвоста «(мест N из M)», регистр не важен); null, если текст не известен. */
    static ShipmentStatusCode normalizeStatus(String cargoStatus) {
        if (cargoStatus == null) {
            return null;
        }
        String key = cargoStatus.toLowerCase(Locale.ROOT);
        int bracket = key.indexOf('(');
        return STATUS_CODES.get((bracket < 0 ? key : key.substring(0, bracket)).trim());
    }

    private JsonNode post(String path, Map<String, Object> body) {
        return http.post().uri(path)
                .headers(h -> h.setBasicAuth(login, apiKey))
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(JsonNode.class);
    }

    /** ПЭК отвечает «грузы не найдены» ошибкой с status 404; любая другая ошибка в теле — исключение, не «не найдено». */
    private static boolean isNotFoundError(JsonNode body) {
        JsonNode error = body == null ? null : body.get("error");
        if (error == null || error.isNull()) {
            return false;
        }
        if (error.path("status").asInt(0) == NOT_FOUND_STATUS) {
            return true;
        }
        throw new CarrierApiException("ПЭК: " + text(error, "title", "message"));
    }

    /** Массив cargos; ответ без него — ошибка, а не «ничего не найдено». */
    private static JsonNode cargosOf(JsonNode body) {
        JsonNode cargos = body == null ? null : body.get("cargos");
        if (cargos == null || !cargos.isArray()) {
            throw new CarrierApiException("Ответ ПЭК не содержит списка грузов");
        }
        return cargos;
    }

    private static CarrierShipmentStatus toStatus(JsonNode cargo) {
        String raw = text(cargo.path("info"), "cargoStatus");
        ShipmentStatusCode code = normalizeStatus(raw);
        if (raw != null && code == null) {
            log.warn("ПЭК: неизвестный статус накладной, код не меняется: {}", raw);
        }
        return new CarrierShipmentStatus(code, raw, cityFrom(cargo), cityTo(cargo));
    }

    private static String cityFrom(JsonNode cargo) {
        return text(cargo.path("sender").path("branchInfo"), "city");
    }

    private static String cityTo(JsonNode cargo) {
        return text(cargo.path("receiver").path("branch"), "city");
    }

    /** Город подразделения из branch.cities по cityId; если не нашёлся — название филиала. */
    private static String cityOf(JsonNode branch, String cityId) {
        for (JsonNode city : branch.path("cities")) {
            if (cityId != null && cityId.equals(text(city, "cityId"))) {
                return text(city, "title");
            }
        }
        return text(branch, "title");
    }

    private static TerminalData toTerminal(JsonNode warehouse, String city) {
        JsonNode coordinates = warehouse.path("coordinatesobj");
        String address = text(warehouse, "addressDivision", "address");
        return new TerminalData(text(warehouse, "id"), PointType.TERMINAL, city, text(warehouse, "name"), address,
                number(coordinates, "latitude"), number(coordinates, "longitude"),
                workingHours(warehouse), text(warehouse, "telephone"));
    }

    /** «Пн 09:00-18:00; Вт …» из divisionTimeOfWork (или timeOfWork); null, если расписания нет. */
    private static String workingHours(JsonNode warehouse) {
        JsonNode days = warehouse.path("divisionTimeOfWork");
        if (days.isEmpty()) {
            days = warehouse.path("timeOfWork");
        }
        StringJoiner joiner = new StringJoiner("; ");
        for (JsonNode day : days) {
            int index = day.path("dayOfWeek").asInt(0) - 1;
            String from = text(day, "workFrom");
            String to = text(day, "workTo");
            if (index >= 0 && index < DAY_NAMES.length && from != null && to != null) {
                joiner.add(DAY_NAMES[index] + " " + from + "-" + to);
            }
        }
        return joiner.length() == 0 ? null : joiner.toString();
    }

    private static void register(ShipmentStatusCode code, String... statuses) {
        for (String status : statuses) {
            STATUS_CODES.put(status, code);
        }
    }
}
