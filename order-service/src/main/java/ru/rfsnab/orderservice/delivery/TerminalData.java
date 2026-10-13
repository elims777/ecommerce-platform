package ru.rfsnab.orderservice.delivery;

/**
 * Точка в том виде, как её отдала ТК, до проверки. Любое поле может быть null:
 * валидность решает синхронизация, чтобы одна битая запись не ломала разбор ответа.
 */
public record TerminalData(
        String externalId,
        PointType pointType,
        String city,
        String name,
        String address,
        Double latitude,
        Double longitude,
        String workingHours,
        String phone
) {
}
