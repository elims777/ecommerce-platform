package ru.rfsnab.orderservice.delivery;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import ru.rfsnab.orderservice.exception.InvalidOrderStateException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("CarrierService")
class CarrierServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Mock
    private CarrierRepository carrierRepository;

    private CarrierClient dellinClient;
    private CarrierService service;

    @BeforeEach
    void setUp() {
        dellinClient = mock(CarrierClient.class);
        when(dellinClient.code()).thenReturn(CarrierCode.DELLIN);
        when(dellinClient.isConfigured()).thenReturn(true);
        CarrierClient cdekClient = mock(CarrierClient.class);
        when(cdekClient.code()).thenReturn(CarrierCode.CDEK);
        when(cdekClient.isConfigured()).thenReturn(false);
        service = new CarrierService(carrierRepository, List.of(dellinClient, cdekClient), objectMapper);
        when(carrierRepository.save(any(Carrier.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private Carrier carrier(CarrierCode code, boolean enabled, boolean terminalEnabled, int activeTerminals) {
        return Carrier.builder().code(code).name(code.name()).enabled(enabled).addressDeliveryEnabled(true)
                .terminalDeliveryEnabled(terminalEnabled).activeTerminalsCount(activeTerminals).sortOrder(1).build();
    }

    private CarrierUpdateRequest request(Boolean terminalDeliveryEnabled) {
        return new CarrierUpdateRequest(true, true, 1, terminalDeliveryEnabled, null);
    }

    @Test
    @DisplayName("requireTerminalDelivery: включённая ТК с terminal_delivery_enabled принимается")
    void acceptsTerminalCarrier() {
        Carrier carrier = carrier(CarrierCode.DELLIN, true, true, 10);
        when(carrierRepository.findById(CarrierCode.DELLIN)).thenReturn(Optional.of(carrier));

        assertThat(service.requireTerminalDelivery(CarrierCode.DELLIN)).isSameAs(carrier);
    }

    @Test
    @DisplayName("requireTerminalDelivery: без terminal_delivery_enabled — отказ")
    void rejectsCarrierWithoutTerminalFlag() {
        when(carrierRepository.findById(CarrierCode.DELLIN)).thenReturn(Optional.of(carrier(CarrierCode.DELLIN, true, false, 10)));

        assertThatThrownBy(() -> service.requireTerminalDelivery(CarrierCode.DELLIN))
                .isInstanceOf(InvalidOrderStateException.class).hasMessageContaining("до терминала");
    }

    @Test
    @DisplayName("requireTerminalDelivery: выключенная ТК — отказ даже с флагом терминалов")
    void rejectsDisabledCarrier() {
        when(carrierRepository.findById(CarrierCode.DELLIN)).thenReturn(Optional.of(carrier(CarrierCode.DELLIN, false, true, 10)));

        assertThatThrownBy(() -> service.requireTerminalDelivery(CarrierCode.DELLIN))
                .isInstanceOf(InvalidOrderStateException.class);
    }

    @Test
    @DisplayName("update: админ включает терминалы при наличии активных точек")
    void adminEnablesTerminalDelivery() {
        when(carrierRepository.findById(CarrierCode.DELLIN)).thenReturn(Optional.of(carrier(CarrierCode.DELLIN, true, false, 5)));

        CarrierDto dto = service.update(CarrierCode.DELLIN, request(true));

        assertThat(dto.terminalDeliveryEnabled()).isTrue();
    }

    @Test
    @DisplayName("update: включить терминалы без активных точек нельзя")
    void cannotEnableWithoutTerminals() {
        when(carrierRepository.findById(CarrierCode.DELLIN)).thenReturn(Optional.of(carrier(CarrierCode.DELLIN, true, false, 0)));

        assertThatThrownBy(() -> service.update(CarrierCode.DELLIN, request(true)))
                .isInstanceOf(InvalidOrderStateException.class).hasMessageContaining("синхронизацию");
    }

    @Test
    @DisplayName("update: terminalDeliveryEnabled=null не меняет флаг, выключить можно всегда")
    void nullKeepsFlagAndFalseDisables() {
        Carrier carrier = carrier(CarrierCode.DELLIN, true, true, 0);
        when(carrierRepository.findById(CarrierCode.DELLIN)).thenReturn(Optional.of(carrier));

        assertThat(service.update(CarrierCode.DELLIN, request(null)).terminalDeliveryEnabled()).isTrue();
        assertThat(service.update(CarrierCode.DELLIN, request(false)).terminalDeliveryEnabled()).isFalse();
    }

    @Test
    @DisplayName("update: trackingUrlTemplate — null не меняет, пустая строка очищает, значение сохраняется")
    void trackingUrlTemplateSemantics() {
        Carrier carrier = carrier(CarrierCode.DELLIN, true, false, 0);
        carrier.setTrackingUrlTemplate("https://old/{number}");
        when(carrierRepository.findById(CarrierCode.DELLIN)).thenReturn(Optional.of(carrier));

        assertThat(service.update(CarrierCode.DELLIN, request(null)).trackingUrlTemplate()).isEqualTo("https://old/{number}");
        assertThat(service.update(CarrierCode.DELLIN, withTemplate("https://new/{number}")).trackingUrlTemplate())
                .isEqualTo("https://new/{number}");
        assertThat(service.update(CarrierCode.DELLIN, withTemplate("")).trackingUrlTemplate()).isNull();
        assertThat(carrier.trackingUrl("123")).isNull();
    }

    @Test
    @DisplayName("trackingUrl подставляет номер накладной в шаблон")
    void trackingUrlSubstitutesNumber() {
        Carrier carrier = carrier(CarrierCode.DELLIN, true, false, 0);
        carrier.setTrackingUrlTemplate("https://www.dellin.ru/tracker/orders/{number}/");

        assertThat(carrier.trackingUrl("12-34")).isEqualTo("https://www.dellin.ru/tracker/orders/12-34/");
    }

    @Test
    @DisplayName("DTO: statusTrackingSupported берётся из клиента")
    void dtoCarriesTrackingSupport() {
        when(dellinClient.supportsStatusTracking()).thenReturn(true);
        when(carrierRepository.findAllByOrderBySortOrderAsc()).thenReturn(List.of(
                carrier(CarrierCode.DELLIN, true, false, 0), carrier(CarrierCode.CDEK, true, false, 0)));

        List<CarrierDto> result = service.getAll();

        assertThat(result.get(0).statusTrackingSupported()).isTrue();
        assertThat(result.get(1).statusTrackingSupported()).isFalse();
    }

    private CarrierUpdateRequest withTemplate(String template) {
        return new CarrierUpdateRequest(true, true, 1, null, template);
    }

    @Test
    @DisplayName("DTO: apiKeyConfigured берётся из клиента, отчёт читается из JSON")
    void dtoCarriesKeyStatusAndReport() throws Exception {
        Carrier dellin = carrier(CarrierCode.DELLIN, true, true, 3);
        dellin.setTerminalsSyncedAt(Instant.parse("2026-10-12T20:00:00Z"));
        dellin.setLastSyncReport(objectMapper.writeValueAsString(new CarrierSyncReport(CarrierCode.DELLIN, "ДЛ",
                SyncStatus.WARNING, 5, 1, 0, 0, 1, List.of(new CarrierSyncReport.SkippedItem("X", "Нет города")), 0,
                null, 120, Instant.parse("2026-10-12T20:00:01Z"))));
        Carrier cdek = carrier(CarrierCode.CDEK, true, false, 0);
        when(carrierRepository.findAllByOrderBySortOrderAsc()).thenReturn(List.of(dellin, cdek));

        List<CarrierDto> result = service.getAll();

        assertThat(result.get(0).apiKeyConfigured()).isTrue();
        assertThat(result.get(0).activeTerminalsCount()).isEqualTo(3);
        assertThat(result.get(0).terminalsSyncedAt()).isEqualTo(Instant.parse("2026-10-12T20:00:00Z"));
        assertThat(result.get(0).lastSyncReport().status()).isEqualTo(SyncStatus.WARNING);
        assertThat(result.get(0).lastSyncReport().skippedItems()).hasSize(1);
        assertThat(result.get(1).apiKeyConfigured()).isFalse();
        assertThat(result.get(1).lastSyncReport()).isNull();
    }

    @Test
    @DisplayName("trackingUrl кодирует номер накладной (не даёт подменить путь/параметры ссылки)")
    void trackingUrlEncodesNumber() {
        Carrier carrier = carrier(CarrierCode.DELLIN, true, false, 0);
        carrier.setTrackingUrlTemplate("https://t.example/{number}");

        assertThat(carrier.trackingUrl("a/b?c=d e")).isEqualTo("https://t.example/a%2Fb%3Fc%3Dd+e");
    }

    @Test
    @DisplayName("getEnabled: клиенту не отдаются отчёт синхронизации, ключ API, дата и число терминалов")
    void clientDtoHidesAdminFields() throws Exception {
        Carrier dellin = carrier(CarrierCode.DELLIN, true, true, 3);
        dellin.setTerminalsSyncedAt(Instant.parse("2026-10-12T20:00:00Z"));
        dellin.setLastSyncReport(objectMapper.writeValueAsString(new CarrierSyncReport(CarrierCode.DELLIN, "ДЛ",
                SyncStatus.OK, 5, 1, 0, 0, 0, List.of(), 0, null, 120, Instant.parse("2026-10-12T20:00:01Z"))));
        when(carrierRepository.findAllByEnabledTrueOrderBySortOrderAsc()).thenReturn(List.of(dellin));

        CarrierDto dto = service.getEnabled().get(0);

        assertThat(dto.lastSyncReport()).isNull();
        assertThat(dto.apiKeyConfigured()).isFalse();
        assertThat(dto.terminalsSyncedAt()).isNull();
        assertThat(dto.activeTerminalsCount()).isZero();
        assertThat(dto.terminalDeliveryEnabled()).isTrue();
        assertThat(dto.name()).isEqualTo("DELLIN");
    }

    @Test
    @DisplayName("DTO: нечитаемый отчёт в БД не ломает список ТК")
    void corruptReportIsIgnored() {
        Carrier dellin = carrier(CarrierCode.DELLIN, true, true, 3);
        dellin.setLastSyncReport("{not json");
        when(carrierRepository.findAllByEnabledTrueOrderBySortOrderAsc()).thenReturn(List.of(dellin));

        assertThat(service.getEnabled().get(0).lastSyncReport()).isNull();
    }
}
