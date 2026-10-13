package ru.rfsnab.orderservice.delivery;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.StringJoiner;

import static ru.rfsnab.orderservice.delivery.CarrierJson.array;
import static ru.rfsnab.orderservice.delivery.CarrierJson.number;
import static ru.rfsnab.orderservice.delivery.CarrierJson.text;

/**
 * ПЭК: POST kabinet.pecom.ru/api/v1/branches/all/, Basic-авторизация логином и ключом API.
 * Точки выдачи — склады branches[].divisions[].warehouses[], которые выдают груз и не закрыты.
 */
@Component
public class PecClient implements CarrierClient {

    private static final String BASE_URL = "https://kabinet.pecom.ru";
    private static final String BRANCHES_PATH = "/api/v1/branches/all/";
    private static final String[] DAY_NAMES = {"Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс"};

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
}
