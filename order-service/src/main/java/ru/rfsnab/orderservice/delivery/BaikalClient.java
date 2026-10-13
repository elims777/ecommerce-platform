package ru.rfsnab.orderservice.delivery;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpClientErrorException;
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
import java.util.stream.StreamSupport;

import static ru.rfsnab.orderservice.delivery.CarrierJson.array;
import static ru.rfsnab.orderservice.delivery.CarrierJson.genericTerminal;
import static ru.rfsnab.orderservice.delivery.CarrierJson.instant;
import static ru.rfsnab.orderservice.delivery.CarrierJson.text;

/**
 * Байкал Сервис: REST api.baikalsr.ru/restapi, ключ API передаётся как Basic-логин с пустым паролем.
 */
@Slf4j
@Component
public class BaikalClient implements CarrierClient {

    private static final String BASE_URL = "https://api.baikalsr.ru/restapi";
    private static final String TERMINALS_PATH = "/terminals";
    // ponytail: пути статуса и журнала предположительные до получения документации Байкал Сервис (публичной нет); сверить на реальных ответах
    private static final String STATUS_PATH = "/orders/status?number={number}";
    private static final String JOURNAL_PATH = "/orders?date_from={from}&date_to={to}";

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

    @Override
    public boolean supportsStatusTracking() {
        return isConfigured();
    }

    @Override
    public boolean supportsJournal() {
        return isConfigured();
    }

    /** Статус по одному запросу на накладную; пути не сверены с докой, поэтому 404 — ошибка, а не «накладной нет». */
    @Override
    public Map<String, CarrierShipmentStatus> fetchStatuses(List<String> waybills) {
        if (waybills.isEmpty()) {
            return Map.of();
        }
        Map<String, CarrierShipmentStatus> result = new HashMap<>();
        for (String waybill : waybills) {
            JsonNode body;
            try {
                body = http.get().uri(STATUS_PATH, waybill)
                        .headers(h -> h.setBasicAuth(apiKey, ""))
                        .retrieve()
                        .body(JsonNode.class);
            } catch (HttpClientErrorException.NotFound e) {
                throw new CarrierApiException("Байкал Сервис ответил 404 — путь статуса не сверен с документацией");
            }
            if (body == null || !body.isObject() || text(body, "status", "state") == null) {
                throw new CarrierApiException("Ответ Байкал Сервис не содержит статуса накладной");
            }
            result.put(waybill, toStatus(body));
        }
        return result;
    }

    @Override
    public List<JournalItem> fetchJournal(Instant since) {
        JsonNode body = http.get().uri(JOURNAL_PATH, LocalDate.ofInstant(since, ZoneOffset.UTC),
                        LocalDate.now(ZoneOffset.UTC))
                .headers(h -> h.setBasicAuth(apiKey, ""))
                .retrieve()
                .body(JsonNode.class);
        List<JournalItem> result = new ArrayList<>();
        for (JsonNode order : array(body, "orders", "data", "result")) {
            String waybill = text(order, "number", "waybill", "id");
            if (waybill != null) {
                result.add(new JournalItem(waybill, text(order, "sender", "senderName"), text(order, "senderInn"),
                        text(order, "receiver", "receiverName"), text(order, "receiverInn"),
                        text(order, "from", "cityFrom"), text(order, "to", "cityTo"),
                        instant(order, "shippedAt", "date", "createdAt"), instant(order, "estimatedDeliveryAt", "deliveryDate"),
                        text(order, "clientNumber", "customNumber"),
                        text(order, "status", "state") == null ? null : toStatus(order)));
            }
        }
        return result;
    }

    /** Код по ключевым словам текста статуса (формат статусов Байкал Сервис неизвестен); неизвестный — WARN и null. */
    static ShipmentStatusCode normalizeStatus(String status) {
        if (status == null) {
            return null;
        }
        String s = status.toLowerCase(Locale.ROOT);
        if (s.contains("не выдан") || s.contains("не доставлен")) {
            return null;
        }
        if (s.contains("отмен") || s.contains("возврат")) {
            return ShipmentStatusCode.CANCELLED;
        }
        if (s.contains("выдан") || s.contains("доставлен")) {
            return ShipmentStatusCode.DELIVERED;
        }
        if (s.contains("прибы")) {
            return ShipmentStatusCode.ARRIVED_AT_TERMINAL;
        }
        if (s.contains("в пути") || s.contains("принят") || s.contains("оформлен") || s.contains("отправлен")) {
            return ShipmentStatusCode.IN_TRANSIT;
        }
        return null;
    }

    private static CarrierShipmentStatus toStatus(JsonNode order) {
        String raw = text(order, "status", "state");
        ShipmentStatusCode code = normalizeStatus(raw);
        if (raw != null && code == null) {
            log.warn("Байкал Сервис: неизвестный статус накладной, код не меняется: {}", raw);
        }
        return new CarrierShipmentStatus(code, raw, text(order, "from", "cityFrom"), text(order, "to", "cityTo"));
    }
}
