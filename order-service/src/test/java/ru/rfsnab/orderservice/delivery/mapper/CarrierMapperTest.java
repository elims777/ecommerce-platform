package ru.rfsnab.orderservice.delivery.mapper;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import ru.rfsnab.orderservice.delivery.client.TerminalData;
import ru.rfsnab.orderservice.delivery.models.dto.CarrierDto;
import ru.rfsnab.orderservice.delivery.models.entity.Carrier;
import ru.rfsnab.orderservice.delivery.models.entity.CarrierTerminal;
import ru.rfsnab.orderservice.delivery.models.entity.enums.CarrierCode;
import ru.rfsnab.orderservice.delivery.models.entity.enums.PointType;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("CarrierMapper")
class CarrierMapperTest {

    private static final Instant NOW = Instant.parse("2026-10-20T10:00:00Z");

    private final CarrierMapper mapper = Mappers.getMapper(CarrierMapper.class);

    private static TerminalData data(String name, Double lat, Double lon) {
        return new TerminalData("ext-1", PointType.PVZ, "Омск", name, "ул. Ленина, 1", lat, lon, "Пн-Пт", "+7 000");
    }

    @Test
    @DisplayName("toTerminal: все поля, активная точка, syncedAt и код ТК из аргументов")
    void toTerminalMapsAllFields() {
        CarrierTerminal t = mapper.toTerminal(data("Пункт", 55.0, 82.9), CarrierCode.CDEK, NOW);

        assertThat(t.getId()).isNull();
        assertThat(t.getCarrierCode()).isEqualTo(CarrierCode.CDEK);
        assertThat(t.getExternalId()).isEqualTo("ext-1");
        assertThat(t.getPointType()).isEqualTo(PointType.PVZ);
        assertThat(t.getCity()).isEqualTo("Омск");
        assertThat(t.getName()).isEqualTo("Пункт");
        assertThat(t.getAddress()).isEqualTo("ул. Ленина, 1");
        assertThat(t.getLatitude()).isEqualByComparingTo("55.0");
        assertThat(t.getLongitude()).isEqualByComparingTo("82.9");
        assertThat(t.getWorkingHours()).isEqualTo("Пн-Пт");
        assertThat(t.getPhone()).isEqualTo("+7 000");
        assertThat(t.isActive()).isTrue();
        assertThat(t.getSyncedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("toTerminal: координаты округляются до 6 знаков HALF_UP")
    void toTerminalRoundsCoordinates() {
        CarrierTerminal t = mapper.toTerminal(data("Пункт", 55.1234565, -82.1234564), CarrierCode.CDEK, NOW);

        assertThat(t.getLatitude()).isEqualTo(new BigDecimal("55.123457"));
        assertThat(t.getLongitude()).isEqualTo(new BigDecimal("-82.123456"));
        assertThat(t.getLatitude().scale()).isEqualTo(6);
    }

    @Test
    @DisplayName("toTerminal: пустое название заменяется адресом")
    void toTerminalBlankNameFallsBackToAddress() {
        assertThat(mapper.toTerminal(data(null, 1.0, 1.0), CarrierCode.CDEK, NOW).getName()).isEqualTo("ул. Ленина, 1");
        assertThat(mapper.toTerminal(data("  ", 1.0, 1.0), CarrierCode.CDEK, NOW).getName()).isEqualTo("ул. Ленина, 1");
    }

    @Test
    @DisplayName("updateTerminal: копирует содержимое, id, active и syncedAt остаются")
    void updateTerminalKeepsIdentityFields() {
        Instant old = Instant.parse("2026-01-01T00:00:00Z");
        CarrierTerminal target = CarrierTerminal.builder().id(7L).carrierCode(CarrierCode.CDEK).externalId("ext-1")
                .pointType(PointType.TERMINAL).city("Томск").name("Старое").address("старый адрес")
                .latitude(BigDecimal.ONE).longitude(BigDecimal.ONE).workingHours("24/7").phone("1")
                .active(false).syncedAt(old).build();
        CarrierTerminal source = mapper.toTerminal(data("Новое", 55.0, 82.9), CarrierCode.CDEK, NOW);
        source.setWorkingHours(null);

        mapper.updateTerminal(target, source);

        assertThat(target.getId()).isEqualTo(7L);
        assertThat(target.isActive()).isFalse();
        assertThat(target.getSyncedAt()).isEqualTo(old);
        assertThat(target.getPointType()).isEqualTo(PointType.PVZ);
        assertThat(target.getCity()).isEqualTo("Омск");
        assertThat(target.getName()).isEqualTo("Новое");
        assertThat(target.getAddress()).isEqualTo("ул. Ленина, 1");
        assertThat(target.getLatitude()).isEqualByComparingTo("55.0");
        assertThat(target.getWorkingHours()).isNull();
        assertThat(target.getPhone()).isEqualTo("+7 000");
    }

    @Test
    @DisplayName("toClientDto: скрыты ключ API, синхронизация и отчёт, statusTrackingSupported сохранён")
    void toClientDtoHidesAdminFields() {
        Carrier carrier = Carrier.builder().code(CarrierCode.PEC).name("ПЭК").enabled(true).sortOrder(3)
                .terminalsSyncedAt(NOW).activeTerminalsCount(42).lastSyncReport("{}")
                .trackingUrlTemplate("https://t.example/{number}").build();

        CarrierDto dto = mapper.toClientDto(carrier, true);

        assertThat(dto.code()).isEqualTo(CarrierCode.PEC);
        assertThat(dto.name()).isEqualTo("ПЭК");
        assertThat(dto.statusTrackingSupported()).isTrue();
        assertThat(dto.trackingUrlTemplate()).isEqualTo("https://t.example/{number}");
        assertThat(dto.apiKeyConfigured()).isFalse();
        assertThat(dto.terminalsSyncedAt()).isNull();
        assertThat(dto.activeTerminalsCount()).isZero();
        assertThat(dto.lastSyncReport()).isNull();
    }
}
