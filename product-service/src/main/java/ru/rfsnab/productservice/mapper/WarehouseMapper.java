package ru.rfsnab.productservice.mapper;

import ru.rfsnab.productservice.dto.ProductStockResponse;
import ru.rfsnab.productservice.dto.WarehouseRequest;
import ru.rfsnab.productservice.dto.WarehouseResponse;
import ru.rfsnab.productservice.model.Warehouse;

public final class WarehouseMapper {

    private WarehouseMapper() {}

    public static Warehouse toEntity(WarehouseRequest request) {
        return Warehouse.builder()
                .code(request.code())
                .name(request.name())
                .city(request.city())
                .pickupAvailable(request.pickupAvailable())
                .active(request.active())
                .sortOrder(request.sortOrder())
                .build();
    }

    /** Переносит поля запроса в сущность, кроме неизменяемого code. */
    public static void updateEntity(Warehouse warehouse, WarehouseRequest request) {
        warehouse.setName(request.name());
        warehouse.setCity(request.city());
        warehouse.setPickupAvailable(request.pickupAvailable());
        warehouse.setActive(request.active());
        warehouse.setSortOrder(request.sortOrder());
    }

    public static WarehouseResponse toResponse(Warehouse warehouse) {
        return new WarehouseResponse(warehouse.getId(), warehouse.getCode(), warehouse.getName(),
                warehouse.getCity(), warehouse.getPickupAvailable(), warehouse.getActive(),
                warehouse.getSortOrder());
    }

    public static ProductStockResponse toStockResponse(Warehouse warehouse, int quantity) {
        return new ProductStockResponse(warehouse.getId(), warehouse.getCode(), warehouse.getName(),
                warehouse.getCity(), quantity);
    }
}
