package ru.rfsnab.productservice.dto;

public record ProductStockResponse(Long warehouseId, String code, String name, String city, int quantity) {
}
