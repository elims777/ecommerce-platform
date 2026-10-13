package ru.rfsnab.orderservice.delivery;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Optional;
import java.util.stream.StreamSupport;

import static ru.rfsnab.orderservice.delivery.CarrierJson.array;
import static ru.rfsnab.orderservice.delivery.CarrierJson.genericTerminal;

/**
 * Байкал Сервис: REST api.baikalsr.ru/restapi, ключ API передаётся как Basic-логин с пустым паролем.
 */
@Component
public class BaikalClient implements CarrierClient {

    private static final String BASE_URL = "https://api.baikalsr.ru/restapi";
    private static final String TERMINALS_PATH = "/terminals";

    private final RestClient http;
    private final String apiKey;

    /** Создаёт клиент; без apiKey клиент отключён. */
    public BaikalClient(RestClient.Builder builder, @Value("${carriers.baikal.api-key:}") String apiKey) {
        this.http = builder.baseUrl(BASE_URL).build();
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

    @Override
    public Optional<List<TerminalData>> fetchTerminals() {
        JsonNode body = http.get().uri(TERMINALS_PATH)
                .headers(h -> h.setBasicAuth(apiKey, ""))
                .retrieve()
                .body(JsonNode.class);
        return Optional.of(StreamSupport.stream(array(body, "terminals", "data", "result").spliterator(), false)
                .map(CarrierJson::genericTerminal)
                .toList());
    }
}
