package ru.rfsnab.orderservice.delivery;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static ru.rfsnab.orderservice.delivery.CarrierJson.array;
import static ru.rfsnab.orderservice.delivery.CarrierJson.number;
import static ru.rfsnab.orderservice.delivery.CarrierJson.text;

/**
 * Возовоз: API 2.5 (github.com/vozovoz/apiv2), объект terminal, действие get, токен в GET-параметре token.
 * Список читается страницами limit/offset.
 */
@Component
public class VozovozClient implements CarrierClient {

    private static final String BASE_URL = "https://vozovoz.ru";
    private static final String API_PATH = "/api/?token={token}";
    private static final int PAGE_SIZE = 1000;

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
        int offset = 0;
        while (true) {
            JsonNode body = http.post().uri(API_PATH, token)
                    .body(Map.of("object", "terminal", "action", "get",
                            "params", Map.of("limit", PAGE_SIZE, "offset", offset)))
                    .retrieve()
                    .body(JsonNode.class);
            String error = body == null ? null : text(body.path("error"), "message");
            if (error != null) {
                throw new CarrierApiException("Возовоз: " + error);
            }
            JsonNode page = array(body, "response", "data");
            page.forEach(item -> result.add(toTerminal(item)));
            // сервер сам урезает limit (отдаёт по 100), поэтому листаем по meta.total; нет total — до пустой страницы
            int total = body.path("response").path("meta").path("total").asInt(0);
            offset += page.size();
            if (page.isEmpty() || (total > 0 && offset >= total)) {
                return Optional.of(result);
            }
        }
    }

    private static TerminalData toTerminal(JsonNode item) {
        JsonNode coordinates = item.path("coordinates");
        return new TerminalData(text(item, "guid"), PointType.TERMINAL, text(item, "location_name"),
                text(item, "name"), text(item, "address"), number(coordinates, "latitude"),
                number(coordinates, "longitude"), null, null);
    }
}
