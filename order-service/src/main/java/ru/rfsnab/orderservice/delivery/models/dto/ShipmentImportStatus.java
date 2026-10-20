package ru.rfsnab.orderservice.delivery.models.dto;

/** Статус импорта журналов ТК: running — импорт идёт прямо сейчас. */
public record ShipmentImportStatus(boolean running) {}
