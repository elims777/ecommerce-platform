package ru.rfsnab.productservice.dto;

public record WarehouseResponse(Long id, String code, String name, String city,
                                boolean pickupAvailable, boolean active, int sortOrder) {
}
