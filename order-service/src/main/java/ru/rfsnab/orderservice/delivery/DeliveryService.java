package ru.rfsnab.orderservice.delivery;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.rfsnab.orderservice.exception.InvalidOrderStateException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Справочник терминалов ТК для оформления заказа: города, точки города (с кэшем в Redis), проверка точки заказа.
 * Redis — только ускорение: при его недоступности запрос идёт в БД.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeliveryService {

    /** Минимальная длина строки поиска города. */
    public static final int MIN_CITY_QUERY_LENGTH = 2;
    /** Максимум городов в ответе поиска. */
    public static final int MAX_CITIES = 20;

    private static final Duration CACHE_TTL = Duration.ofHours(12);
    private static final String CACHE_PREFIX = "delivery:terminals:";
    private static final long SCAN_BATCH = 100;
    private static final TypeReference<List<CarrierTerminalDto>> TERMINAL_LIST = new TypeReference<>() {
    };

    private final CarrierTerminalRepository terminalRepository;
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    /**
     * Города с активными точками ТК, название которых начинается с q (без учёта регистра).
     *
     * @return до {@link #MAX_CITIES} городов по алфавиту; пустой список, если q короче {@link #MIN_CITY_QUERY_LENGTH}
     */
    @Transactional(readOnly = true)
    public List<String> findCities(CarrierCode carrier, PointType type, String q) {
        String prefix = q == null ? "" : q.trim();
        if (prefix.length() < MIN_CITY_QUERY_LENGTH) {
            return List.of();
        }
        // \, % и _ в строке поиска — обычные символы, а не шаблон LIKE
        String escaped = prefix.toLowerCase(Locale.ROOT).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        return terminalRepository.findCities(carrier, type, escaped, PageRequest.of(0, MAX_CITIES));
    }

    /**
     * Активные точки города одного типа. Результат кэшируется в Redis по (ТК, город, тип).
     */
    @Transactional(readOnly = true)
    public List<CarrierTerminalDto> getTerminals(CarrierCode carrier, String city, PointType type) {
        String key = CACHE_PREFIX + carrier + ":" + type + ":" + city;
        List<CarrierTerminalDto> cached = readCache(key);
        if (cached != null) {
            return cached;
        }
        List<CarrierTerminalDto> terminals = terminalRepository
                .findAllByCarrierCodeAndCityAndPointTypeAndActiveTrueOrderByNameAsc(carrier, city, type)
                .stream().map(CarrierTerminalDto::from).toList();
        // пустой результат не кэшируем: точки могут появиться после синхронизации, а ключ жил бы до 12 часов
        if (!terminals.isEmpty()) {
            writeCache(key, terminals);
        }
        return terminals;
    }

    /** Сбрасывает кэш точек ТК — после синхронизации (SCAN, не KEYS). Недоступный Redis не считается ошибкой. */
    public void evictCache(CarrierCode carrier) {
        ScanOptions options = ScanOptions.scanOptions().match(CACHE_PREFIX + carrier + ":*").count(SCAN_BATCH).build();
        try (Cursor<String> cursor = redis.scan(options)) {
            List<String> keys = new ArrayList<>();
            cursor.forEachRemaining(keys::add);
            if (!keys.isEmpty()) {
                redis.delete(keys);
            }
        } catch (RuntimeException e) {
            log.warn("Не удалось сбросить кэш терминалов {}: {}", carrier, e.getMessage());
        }
    }

    /**
     * Активная точка именно этой ТК — для проверки и снимка при оформлении заказа.
     *
     * @throws InvalidOrderStateException если точки нет, она неактивна или принадлежит другой ТК
     */
    @Transactional(readOnly = true)
    public CarrierTerminal requireActiveTerminal(CarrierCode carrier, Long terminalId) {
        CarrierTerminal terminal = terminalRepository.findById(terminalId)
                .orElseThrow(() -> new InvalidOrderStateException("Выбранный терминал не найден"));
        if (terminal.getCarrierCode() != carrier || !terminal.isActive()) {
            throw new InvalidOrderStateException("Выбранный терминал недоступен");
        }
        return terminal;
    }

    /** Точка по id независимо от активности — для показа заказа. */
    @Transactional(readOnly = true)
    public Optional<CarrierTerminal> findTerminal(Long terminalId) {
        return terminalRepository.findById(terminalId);
    }

    private List<CarrierTerminalDto> readCache(String key) {
        try {
            String json = redis.opsForValue().get(key);
            return json == null ? null : objectMapper.readValue(json, TERMINAL_LIST);
        } catch (JsonProcessingException | RuntimeException e) {
            log.warn("Кэш терминалов недоступен, читаем из БД: {}", e.getMessage());
            return null;
        }
    }

    private void writeCache(String key, List<CarrierTerminalDto> terminals) {
        try {
            redis.opsForValue().set(key, objectMapper.writeValueAsString(terminals), CACHE_TTL);
        } catch (JsonProcessingException | RuntimeException e) {
            log.warn("Не удалось записать кэш терминалов: {}", e.getMessage());
        }
    }
}
