package ru.rfsnab.orderservice.delivery.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import ru.rfsnab.orderservice.BaseIntegrationTest;
import ru.rfsnab.orderservice.delivery.models.entity.CarrierTerminal;
import ru.rfsnab.orderservice.delivery.models.entity.enums.CarrierCode;
import ru.rfsnab.orderservice.delivery.models.entity.enums.PointType;
import ru.rfsnab.orderservice.delivery.service.DeliveryService;
import ru.rfsnab.orderservice.repository.OrderRepository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Запросы по точкам ТК на реальной PostgreSQL: поиск городов (через DeliveryService, который экранирует LIKE)
 * и выборка точек города.
 */
@DisplayName("CarrierTerminalRepository / DeliveryService.findCities")
class CarrierTerminalRepositoryIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private CarrierTerminalRepository terminalRepository;
    @Autowired
    private OrderRepository orderRepository;
    @Autowired
    private DeliveryService deliveryService;

    @BeforeEach
    void clean() {
        orderRepository.deleteAll();
        terminalRepository.deleteAll();
    }

    private CarrierTerminal save(CarrierCode carrier, PointType type, String city, String name, boolean active) {
        return terminalRepository.save(CarrierTerminal.builder().carrierCode(carrier)
                .externalId(carrier + "-" + type + "-" + city + "-" + name).pointType(type).city(city).name(name)
                .address("ул. Тестовая, 1").latitude(new BigDecimal("55.000000")).longitude(new BigDecimal("82.000000"))
                .active(active).syncedAt(Instant.now()).build());
    }

    private List<String> cities(String q) {
        return deliveryService.findCities(CarrierCode.DELLIN, PointType.TERMINAL, q);
    }

    @Test
    @DisplayName("города: префикс без учёта регистра, кириллица, distinct, сортировка по алфавиту")
    void citiesByPrefix() {
        save(CarrierCode.DELLIN, PointType.TERMINAL, "Новосибирск", "Т1", true);
        save(CarrierCode.DELLIN, PointType.TERMINAL, "Новосибирск", "Т2", true);
        save(CarrierCode.DELLIN, PointType.TERMINAL, "Новокузнецк", "Т3", true);
        save(CarrierCode.DELLIN, PointType.TERMINAL, "Омск", "Т4", true);

        assertThat(cities("ново")).containsExactly("Новокузнецк", "Новосибирск");
        assertThat(cities("НОВОС")).containsExactly("Новосибирск");
        assertThat(cities("  новоси  ")).containsExactly("Новосибирск");
        assertThat(cities("ск")).isEmpty();
    }

    @Test
    @DisplayName("города: только активные точки нужной ТК и нужного типа")
    void citiesOnlyActiveOfCarrierAndType() {
        save(CarrierCode.DELLIN, PointType.TERMINAL, "Новоалтайск", "закрыт", false);
        save(CarrierCode.CDEK, PointType.TERMINAL, "Новгород", "чужая ТК", true);
        save(CarrierCode.DELLIN, PointType.PVZ, "Новороссийск", "другой тип", true);
        save(CarrierCode.DELLIN, PointType.TERMINAL, "Новосибирск", "живая", true);

        assertThat(cities("нов")).containsExactly("Новосибирск");
    }

    @Test
    @DisplayName("города: % и _ в запросе — обычные символы, а не шаблон LIKE")
    void citiesEscapeLikeWildcards() {
        save(CarrierCode.DELLIN, PointType.TERMINAL, "Town_1", "a", true);
        save(CarrierCode.DELLIN, PointType.TERMINAL, "Townx1", "b", true);
        save(CarrierCode.DELLIN, PointType.TERMINAL, "50%city", "c", true);
        save(CarrierCode.DELLIN, PointType.TERMINAL, "500city", "d", true);

        assertThat(cities("town_")).containsExactly("Town_1");
        assertThat(cities("50%")).containsExactly("50%city");
        assertThat(cities("%%")).isEmpty();
    }

    @Test
    @DisplayName("города: не больше 20 в ответе")
    void citiesLimited() {
        IntStream.range(0, DeliveryService.MAX_CITIES + 5)
                .forEach(i -> save(CarrierCode.DELLIN, PointType.TERMINAL, String.format("Город%02d", i), "т", true));

        assertThat(cities("гор")).hasSize(DeliveryService.MAX_CITIES).first().isEqualTo("Город00");
        assertThat(terminalRepository.findCities(CarrierCode.DELLIN, PointType.TERMINAL, "гор", PageRequest.of(0, 3))).hasSize(3);
    }

    @Test
    @DisplayName("точки города: фильтр по ТК, городу, типу и активности, сортировка по названию")
    void terminalsOfCity() {
        save(CarrierCode.DELLIN, PointType.TERMINAL, "Омск", "Б-терминал", true);
        save(CarrierCode.DELLIN, PointType.TERMINAL, "Омск", "А-терминал", true);
        save(CarrierCode.DELLIN, PointType.TERMINAL, "Омск", "В-закрытый", false);
        save(CarrierCode.DELLIN, PointType.TERMINAL, "Томск", "Другой город", true);
        save(CarrierCode.DELLIN, PointType.PVZ, "Омск", "Другой тип", true);
        save(CarrierCode.CDEK, PointType.TERMINAL, "Омск", "Другая ТК", true);

        List<CarrierTerminal> result = terminalRepository
                .findAllByCarrierCodeAndCityAndPointTypeAndActiveTrueOrderByNameAsc(CarrierCode.DELLIN, "Омск", PointType.TERMINAL);

        assertThat(result).extracting(CarrierTerminal::getName).containsExactly("А-терминал", "Б-терминал");
    }
}
