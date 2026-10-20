package ru.rfsnab.orderservice.delivery.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import ru.rfsnab.orderservice.delivery.client.TerminalData;
import ru.rfsnab.orderservice.delivery.models.dto.CarrierDto;
import ru.rfsnab.orderservice.delivery.models.dto.CarrierSyncReport;
import ru.rfsnab.orderservice.delivery.models.dto.CarrierTerminalDto;
import ru.rfsnab.orderservice.delivery.models.entity.Carrier;
import ru.rfsnab.orderservice.delivery.models.entity.CarrierTerminal;
import ru.rfsnab.orderservice.delivery.models.entity.enums.CarrierCode;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

@Mapper(componentModel = "spring")
public interface CarrierMapper {

    /** Знаков после запятой у широты и долготы (precision 9, scale 6 в БД). */
    int COORDINATE_SCALE = 6;

    @Mapping(target = "lastSyncReport", source = "lastSyncReport")
    CarrierDto toDto(Carrier carrier, boolean apiKeyConfigured, CarrierSyncReport lastSyncReport,
                     boolean statusTrackingSupported);

    /** Копия для клиента: без служебных полей синхронизации и состояния ключа API. */
    @Mapping(target = "apiKeyConfigured", ignore = true)
    @Mapping(target = "terminalsSyncedAt", ignore = true)
    @Mapping(target = "activeTerminalsCount", ignore = true)
    @Mapping(target = "lastSyncReport", ignore = true)
    CarrierDto toClientDto(Carrier carrier, boolean statusTrackingSupported);

    CarrierTerminalDto toTerminalDto(CarrierTerminal terminal);

    /** Новая активная точка из ответа ТК: координаты округляются до 6 знаков, пустое название заменяется адресом. */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "carrierCode", source = "code")
    @Mapping(target = "externalId", source = "data.externalId")
    @Mapping(target = "pointType", source = "data.pointType")
    @Mapping(target = "city", source = "data.city")
    @Mapping(target = "name",
            expression = "java(org.springframework.util.StringUtils.hasText(data.name()) ? data.name() : data.address())")
    @Mapping(target = "address", source = "data.address")
    @Mapping(target = "latitude", source = "data.latitude", qualifiedByName = "coordinate")
    @Mapping(target = "longitude", source = "data.longitude", qualifiedByName = "coordinate")
    @Mapping(target = "workingHours", source = "data.workingHours")
    @Mapping(target = "phone", source = "data.phone")
    @Mapping(target = "active", constant = "true")
    @Mapping(target = "syncedAt", source = "syncedAt")
    CarrierTerminal toTerminal(TerminalData data, CarrierCode code, Instant syncedAt);

    /** Копирует содержимое точки (без id, ключа, active и syncedAt). */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "carrierCode", ignore = true)
    @Mapping(target = "externalId", ignore = true)
    @Mapping(target = "active", ignore = true)
    @Mapping(target = "syncedAt", ignore = true)
    void updateTerminal(@MappingTarget CarrierTerminal target, CarrierTerminal source);

    @Named("coordinate")
    default BigDecimal toCoordinate(Double value) {
        return BigDecimal.valueOf(value).setScale(COORDINATE_SCALE, RoundingMode.HALF_UP);
    }
}
