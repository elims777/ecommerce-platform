package ru.rfsnab.orderservice.mapper;

import ru.rfsnab.orderservice.models.dto.order.WarehousePointDto;
import ru.rfsnab.orderservice.models.entity.WarehousePoint;

public class WarehousePointMapper {
    public static WarehousePointDto mapToWarehousePointDto(WarehousePoint entity){
        return WarehousePointDto.builder()
                .id(entity.getId())
                .name(entity.getName())
                .city(entity.getCity())
                .street(entity.getStreet())
                .building(entity.getBuilding())
                .postalCode(entity.getPostalCode())
                .phone(entity.getPhoneNumber())
                .description(entity.getDescription())
                .workingHours(entity.getWorkingHours())
                .active(entity.isActive())
                .build();
    }
}
