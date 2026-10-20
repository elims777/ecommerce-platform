package ru.rfsnab.orderservice.delivery.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.MissingNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import ru.rfsnab.orderservice.delivery.exception.CarrierApiException;
import ru.rfsnab.orderservice.delivery.models.entity.ShipmentPackage;
import ru.rfsnab.orderservice.delivery.models.entity.ShipmentPayer;
import ru.rfsnab.orderservice.delivery.models.entity.enums.CarrierCode;
import ru.rfsnab.orderservice.delivery.models.entity.enums.PointType;
import ru.rfsnab.orderservice.delivery.models.entity.enums.ShipmentPayerRole;
import ru.rfsnab.orderservice.delivery.models.entity.enums.ShipmentStatusCode;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.StringJoiner;

import static ru.rfsnab.orderservice.delivery.client.CarrierJson.array;
import static ru.rfsnab.orderservice.delivery.client.CarrierJson.bool;
import static ru.rfsnab.orderservice.delivery.client.CarrierJson.decimal;
import static ru.rfsnab.orderservice.delivery.client.CarrierJson.instant;
import static ru.rfsnab.orderservice.delivery.client.CarrierJson.integer;
import static ru.rfsnab.orderservice.delivery.client.CarrierJson.number;
import static ru.rfsnab.orderservice.delivery.client.CarrierJson.text;

/**
 * ПЭК: POST kabinet.pecom.ru/api/v1/branches/all/, Basic-авторизация логином и ключом API.
 * Точки выдачи — склады branches[].divisions[].warehouses[], которые выдают груз и не закрыты.
 */
@Slf4j
@Component
public class PecClient implements CarrierClient {

    private static final String BRANCHES_PATH = "/api/v1/branches/all/";
    private static final String STATUS_PATH = "/api/v1/cargos/status/";
    private static final String LIST_PATH = "/api/v1/cargos/listallorderbylogin/";
    /** selectBy: 0 — по дате приёмки груза на склад ПЭК. */
    private static final int SELECT_BY_STOCK_DATE = 0;
    private static final int STATUS_BATCH_SIZE = 15;
    private static final int NOT_FOUND_STATUS = 404;
    /** services.items[].payerType: 1 — отправитель, 2 — получатель, 3 — третье лицо. */
    private static final int PAYER_TYPE_SENDER = 1;
    private static final int PAYER_TYPE_RECEIVER = 2;
    private static final int PAYER_TYPE_THIRD = 3;
    private static final String[] DAY_NAMES = {"Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс"};

    /** Тексты cargos[].info.cargoStatus из документации ПЭК (kabinet.pecom.ru/api/v1/help/cargos, в нижнем регистре) → ShipmentStatusCode. */
    private static final Map<String, ShipmentStatusCode> STATUS_CODES = new HashMap<>();

    static {
        register(ShipmentStatusCode.CREATED, "заявка на забор зарегистрирована", "ожидается передача груза от отправителя");
        register(ShipmentStatusCode.IN_TRANSIT, "принят к перевозке", "принят на пвз", "оформлен", "в пути",
                "в пути на терминал", "выполняется адресная доставка");
        register(ShipmentStatusCode.ARRIVED_AT_TERMINAL, "прибыл", "прибыл частично", "разгружается. ожидайте оповещения");
        register(ShipmentStatusCode.DELIVERED, "выдан получателю", "доставлен получателю", "выдан");
        register(ShipmentStatusCode.CANCELLED, "аннулировано до приемки груза", "возвращен отправителю", "утилизирован");
        register(ShipmentStatusCode.PROBLEM, "отправлен на возврат", "изъят на таможне");
    }

    private final RestClient http;
    private final String login;
    private final String apiKey;

    /** Создаёт клиент; без login и apiKey клиент отключён. */
    public PecClient(RestClient.Builder builder,
                     @Value("${carriers.pec.base-url}") String baseUrl,
                     @Value("${carriers.pec.login:}") String login,
                     @Value("${carriers.pec.api-key:}") String apiKey) {
        this.http = builder.baseUrl(baseUrl).build();
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
        Map<String, CarrierShipmentStatus> result = new HashMap<>();
        statusNodes(waybills).forEach((code, node) -> result.put(code, toStatus(node, null, null)));
        return result;
    }

    /**
     * Журнал: /cargos/listallorderbylogin/ даёт коды грузов, заказ забора/доставки до адреса и ИНН сторон;
     * вес, сумма, города, даты и статус — из /cargos/status/ пачками. isIntake/isDelivery точнее признаков из статуса.
     */
    @Override
    public List<JournalItem> fetchJournal(Instant since) {
        JsonNode body = post(LIST_PATH, Map.of("selectBy", SELECT_BY_STOCK_DATE,
                "dateBegin", LocalDate.ofInstant(since, ZoneOffset.UTC).toString(),
                "dateEnd", LocalDate.now(ZoneOffset.UTC).toString()));
        Map<String, JsonNode> listed = new LinkedHashMap<>();
        for (JsonNode item : cargosOf(body)) {
            String code = text(item, "code");
            if (code != null) {
                listed.putIfAbsent(code, item);
            }
        }
        Map<String, JsonNode> statuses = statusNodes(new ArrayList<>(listed.keySet()));
        List<JournalItem> result = new ArrayList<>();
        listed.forEach((code, item) -> {
            JsonNode node = statuses.getOrDefault(code, MissingNode.getInstance());
            JsonNode info = node.path("info");
            Boolean fromAddress = bool(item.path("sender"), "isIntake");
            Boolean toAddress = bool(item.path("receiver"), "isDelivery");
            JsonNode senderParty = item.path("sender").path("counterpart");
            JsonNode receiverParty = item.path("receiver").path("counterpart");
            result.add(new JournalItem(code,
                    firstNonNull(text(node.path("sender"), "sender"), text(senderParty, "name")),
                    text(senderParty.path("legalEntity"), "inn"),
                    firstNonNull(text(node.path("receiver"), "receiver"), text(receiverParty, "name")),
                    text(receiverParty.path("legalEntity"), "inn"),
                    cityFrom(node), cityTo(node),
                    instant(info, "sendingDateTime", "takeOnStockDateTime"),
                    instant(info, "arrivalPlanDateTime", "deliveryPlanDate"),
                    firstNonNull(text(node.path("cargo"), "orderNumber"), text(item, "orderNumber")),
                    text(info, "cargoStatus") == null ? null : toStatus(node, fromAddress, toAddress),
                    toCargo(node, fromAddress, toAddress)));
        });
        return result;
    }

    /** Ответы /cargos/status/ по кодам (пачками); код, которого нет в ответе, в карту не попадает. */
    private Map<String, JsonNode> statusNodes(List<String> waybills) {
        Map<String, JsonNode> result = new HashMap<>();
        for (int from = 0; from < waybills.size(); from += STATUS_BATCH_SIZE) {
            List<String> batch = waybills.subList(from, Math.min(from + STATUS_BATCH_SIZE, waybills.size()));
            JsonNode body = post(STATUS_PATH, Map.of("cargoCodes", batch));
            if (isNotFoundError(body)) {
                continue;
            }
            for (JsonNode cargo : cargosOf(body)) {
                String code = text(cargo.path("cargo"), "code");
                if (code != null && waybills.contains(code)) {
                    result.put(code, cargo);
                }
            }
        }
        return result;
    }

    private static String firstNonNull(String first, String second) {
        return first != null ? first : second;
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

    private static CarrierShipmentStatus toStatus(JsonNode cargo, Boolean fromAddress, Boolean toAddress) {
        String raw = text(cargo.path("info"), "cargoStatus");
        ShipmentStatusCode code = normalizeStatus(raw);
        if (raw != null && code == null) {
            log.warn("ПЭК: неизвестный статус накладной, код не меняется: {}", raw);
        }
        return new CarrierShipmentStatus(code, raw, cityFrom(cargo), cityTo(cargo), toCargo(cargo, fromAddress, toAddress));
    }

    /**
     * Груз из ответа /cargos/status/: оплачено, если задолженности нет (debt == 0); забор от адреса — если заполнен
     * sender.intakeAddress; доставка до адреса — только по наличию плановой даты доставки до двери, иначе неизвестно.
     * fromOverride/toOverride (isIntake/isDelivery журнала), если не null, заменяют эти признаки.
     */
    private static ShipmentCargo toCargo(JsonNode cargo, Boolean fromOverride, Boolean toOverride) {
        JsonNode info = cargo.path("info");
        JsonNode goods = cargo.path("cargo");
        JsonNode services = cargo.path("services");
        BigDecimal debt = decimal(services, "debt");
        JsonNode sender = cargo.path("sender");
        JsonNode barcodes = goods.get("positionBarCodes");
        List<ShipmentPackage> packages = barcodes == null || !barcodes.isArray() ? null : new ArrayList<>();
        if (packages != null) {
            barcodes.forEach(barcode -> packages.add(new ShipmentPackage(barcode.asText(), null, null, null, null)));
        }
        return new ShipmentCargo(decimal(goods, "weight"), decimal(goods, "volume"), integer(goods, "amount"),
                decimal(services, "sum"), debt == null ? null : debt.signum() == 0,
                fromOverride != null ? fromOverride : sender.isObject() ? text(sender, "intakeAddress") != null : null,
                toOverride != null ? toOverride : text(info, "deliveryPlanDate") != null ? Boolean.TRUE : null,
                instant(info, "receivedByClientDateTime", "giveOutDateTime"), null, packages, payersOf(services));
    }

    /**
     * Плательщики из services.items[]: payerType 1 — отправитель, 2 — получатель, 3 — третье лицо; дубли (роль + название) схлопываются.
     * ponytail: формат items взят из документации, на живых данных не проверен (грузов ПЭК пока 0).
     */
    private static List<ShipmentPayer> payersOf(JsonNode services) {
        Map<String, ShipmentPayer> payers = new LinkedHashMap<>();
        for (JsonNode item : services.path("items")) {
            String name = text(item, "payerName");
            Integer type = integer(item, "payerType");
            ShipmentPayerRole role = type == null ? null : switch (type) {
                case PAYER_TYPE_SENDER -> ShipmentPayerRole.SENDER;
                case PAYER_TYPE_RECEIVER -> ShipmentPayerRole.RECEIVER;
                case PAYER_TYPE_THIRD -> ShipmentPayerRole.THIRD;
                default -> null;
            };
            if (name != null) {
                payers.putIfAbsent(role + "|" + name, new ShipmentPayer(name, null, role));
            }
        }
        return payers.isEmpty() ? null : List.copyOf(payers.values());
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
