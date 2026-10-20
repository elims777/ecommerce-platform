package ru.rfsnab.orderservice.delivery.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import ru.rfsnab.orderservice.delivery.mapper.CarrierMapper;
import ru.rfsnab.orderservice.delivery.models.dto.CarrierTerminalDto;
import ru.rfsnab.orderservice.delivery.models.entity.CarrierTerminal;
import ru.rfsnab.orderservice.delivery.models.entity.enums.CarrierCode;
import ru.rfsnab.orderservice.delivery.models.entity.enums.PointType;
import ru.rfsnab.orderservice.delivery.repository.CarrierTerminalRepository;
import ru.rfsnab.orderservice.exception.InvalidOrderStateException;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("DeliveryService")
class DeliveryServiceTest {

    private static final String KEY = "delivery:terminals:DELLIN:TERMINAL:Новосибирск";

    @Mock
    private CarrierTerminalRepository terminalRepository;
    @Mock
    private StringRedisTemplate redis;
    @Mock
    private ValueOperations<String, String> valueOps;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final CarrierMapper carrierMapper = Mappers.getMapper(CarrierMapper.class);
    private DeliveryService service;

    @BeforeEach
    void setUp() {
        when(redis.opsForValue()).thenReturn(valueOps);
        service = new DeliveryService(terminalRepository, redis, objectMapper, carrierMapper);
    }

    private static CarrierTerminal terminal(long id, CarrierCode carrier, boolean active) {
        return CarrierTerminal.builder().id(id).carrierCode(carrier).externalId("E" + id).pointType(PointType.TERMINAL)
                .city("Новосибирск").name("Терминал " + id).address("ул. Ленина, " + id)
                .latitude(new BigDecimal("55.030000")).longitude(new BigDecimal("82.920000")).active(active).build();
    }

    @Test
    @DisplayName("города: меньше 2 символов — пустой список без запроса в БД")
    void shortQueryReturnsEmpty() {
        assertThat(service.findCities(CarrierCode.DELLIN, PointType.TERMINAL, "н")).isEmpty();
        assertThat(service.findCities(CarrierCode.DELLIN, PointType.TERMINAL, " н ")).isEmpty();
        assertThat(service.findCities(CarrierCode.DELLIN, PointType.TERMINAL, null)).isEmpty();
        verify(terminalRepository, never()).findCities(any(), any(), anyString(), any());
    }

    @Test
    @DisplayName("города: префикс в нижнем регистре, % и _ экранированы, лимит 20")
    void citiesQueryIsLowercasedEscapedAndLimited() {
        when(terminalRepository.findCities(eq(CarrierCode.DELLIN), eq(PointType.TERMINAL), anyString(), any(Pageable.class)))
                .thenReturn(List.of("Новосибирск"));

        List<String> result = service.findCities(CarrierCode.DELLIN, PointType.TERMINAL, "  НОВОС%_ ");

        assertThat(result).containsExactly("Новосибирск");
        ArgumentCaptor<String> prefix = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Pageable> page = ArgumentCaptor.forClass(Pageable.class);
        verify(terminalRepository).findCities(eq(CarrierCode.DELLIN), eq(PointType.TERMINAL), prefix.capture(), page.capture());
        assertThat(prefix.getValue()).isEqualTo("новос\\%\\_");
        assertThat(page.getValue().getPageSize()).isEqualTo(DeliveryService.MAX_CITIES);
    }

    @Test
    @DisplayName("терминалы: промах кэша — читаем из БД и кладём в Redis с TTL")
    void cacheMissReadsDbAndStores() {
        when(valueOps.get(KEY)).thenReturn(null);
        when(terminalRepository.findAllByCarrierCodeAndCityAndPointTypeAndActiveTrueOrderByNameAsc(
                CarrierCode.DELLIN, "Новосибирск", PointType.TERMINAL)).thenReturn(List.of(terminal(1, CarrierCode.DELLIN, true)));

        List<CarrierTerminalDto> result = service.getTerminals(CarrierCode.DELLIN, "Новосибирск", PointType.TERMINAL);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).address()).isEqualTo("ул. Ленина, 1");
        assertThat(result.get(0).latitude()).isEqualTo(55.03);
        verify(valueOps).set(eq(KEY), anyString(), any(Duration.class));
    }

    @Test
    @DisplayName("терминалы: попадание в кэш — БД не трогаем")
    void cacheHitSkipsDb() throws Exception {
        String json = objectMapper.writeValueAsString(List.of(carrierMapper.toTerminalDto(terminal(2, CarrierCode.DELLIN, true))));
        when(valueOps.get(KEY)).thenReturn(json);

        List<CarrierTerminalDto> result = service.getTerminals(CarrierCode.DELLIN, "Новосибирск", PointType.TERMINAL);

        assertThat(result).extracting(CarrierTerminalDto::id).containsExactly(2L);
        verify(terminalRepository, never())
                .findAllByCarrierCodeAndCityAndPointTypeAndActiveTrueOrderByNameAsc(any(), anyString(), any());
    }

    @Test
    @DisplayName("терминалы: Redis недоступен — ответ из БД, запись в кэш не роняет запрос")
    void redisDownFallsBackToDb() {
        when(valueOps.get(KEY)).thenThrow(new RedisConnectionFailureException("redis down"));
        doThrow(new RedisConnectionFailureException("redis down"))
                .when(valueOps).set(anyString(), anyString(), any(Duration.class));
        when(terminalRepository.findAllByCarrierCodeAndCityAndPointTypeAndActiveTrueOrderByNameAsc(
                CarrierCode.DELLIN, "Новосибирск", PointType.TERMINAL)).thenReturn(List.of(terminal(3, CarrierCode.DELLIN, true)));

        List<CarrierTerminalDto> result = service.getTerminals(CarrierCode.DELLIN, "Новосибирск", PointType.TERMINAL);

        assertThat(result).extracting(CarrierTerminalDto::id).containsExactly(3L);
    }

    @Test
    @DisplayName("терминалы: испорченное значение в кэше игнорируется, читаем из БД")
    void corruptCacheFallsBackToDb() {
        when(valueOps.get(KEY)).thenReturn("not-json");
        when(terminalRepository.findAllByCarrierCodeAndCityAndPointTypeAndActiveTrueOrderByNameAsc(
                CarrierCode.DELLIN, "Новосибирск", PointType.TERMINAL)).thenReturn(List.of(terminal(4, CarrierCode.DELLIN, true)));

        assertThat(service.getTerminals(CarrierCode.DELLIN, "Новосибирск", PointType.TERMINAL)).hasSize(1);
    }

    @Test
    @DisplayName("терминалы: пустой результат в кэш не кладётся")
    void emptyResultIsNotCached() {
        when(valueOps.get(KEY)).thenReturn(null);
        when(terminalRepository.findAllByCarrierCodeAndCityAndPointTypeAndActiveTrueOrderByNameAsc(
                CarrierCode.DELLIN, "Новосибирск", PointType.TERMINAL)).thenReturn(List.of());

        assertThat(service.getTerminals(CarrierCode.DELLIN, "Новосибирск", PointType.TERMINAL)).isEmpty();

        verify(valueOps, never()).set(anyString(), anyString(), any(Duration.class));
    }

    @SuppressWarnings("unchecked")
    @Test
    @DisplayName("сброс кэша: SCAN по маске ТК и удаление найденных ключей (без KEYS)")
    void evictDeletesCarrierKeys() {
        Cursor<String> cursor = mock(Cursor.class);
        when(cursor.hasNext()).thenReturn(true, false);
        when(cursor.next()).thenReturn(KEY);
        doCallRealMethod().when(cursor).forEachRemaining(any());
        when(redis.scan(any(ScanOptions.class))).thenReturn(cursor);

        service.evictCache(CarrierCode.DELLIN);

        ArgumentCaptor<ScanOptions> options = ArgumentCaptor.forClass(ScanOptions.class);
        verify(redis).scan(options.capture());
        assertThat(options.getValue().getPattern()).isEqualTo("delivery:terminals:DELLIN:*");
        verify(redis).delete(List.of(KEY));
        verify(redis, never()).keys(anyString());
        verify(cursor).close();
    }

    @Test
    @DisplayName("сброс кэша: недоступный Redis не бросает исключение")
    void evictToleratesRedisFailure() {
        when(redis.scan(any(ScanOptions.class))).thenThrow(new QueryTimeoutException("timeout"));

        assertThatCode(() -> service.evictCache(CarrierCode.DELLIN)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("requireActiveTerminal: активная точка этой ТК принимается")
    void acceptsActiveTerminalOfSameCarrier() {
        CarrierTerminal terminal = terminal(5, CarrierCode.DELLIN, true);
        when(terminalRepository.findById(5L)).thenReturn(Optional.of(terminal));

        assertThat(service.requireActiveTerminal(CarrierCode.DELLIN, 5L)).isSameAs(terminal);
    }

    @Test
    @DisplayName("requireActiveTerminal: точка чужой ТК отклоняется")
    void rejectsForeignCarrierTerminal() {
        when(terminalRepository.findById(6L)).thenReturn(Optional.of(terminal(6, CarrierCode.CDEK, true)));

        assertThatThrownBy(() -> service.requireActiveTerminal(CarrierCode.DELLIN, 6L))
                .isInstanceOf(InvalidOrderStateException.class).hasMessageContaining("недоступен");
    }

    @Test
    @DisplayName("requireActiveTerminal: неактивная точка отклоняется")
    void rejectsInactiveTerminal() {
        when(terminalRepository.findById(7L)).thenReturn(Optional.of(terminal(7, CarrierCode.DELLIN, false)));

        assertThatThrownBy(() -> service.requireActiveTerminal(CarrierCode.DELLIN, 7L))
                .isInstanceOf(InvalidOrderStateException.class).hasMessageContaining("недоступен");
    }

    @Test
    @DisplayName("requireActiveTerminal: несуществующая точка отклоняется")
    void rejectsMissingTerminal() {
        when(terminalRepository.findById(8L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.requireActiveTerminal(CarrierCode.DELLIN, 8L))
                .isInstanceOf(InvalidOrderStateException.class).hasMessageContaining("не найден");
    }
}
