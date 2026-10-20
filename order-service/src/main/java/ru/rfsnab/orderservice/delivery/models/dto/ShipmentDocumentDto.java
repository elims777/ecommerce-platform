package ru.rfsnab.orderservice.delivery.models.dto;

/** Документ отправления для фронта: без внутренней ссылки ТК. */
public record ShipmentDocumentDto(String code, String name) {
}
