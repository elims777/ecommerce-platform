package ru.rfsnab.orderservice.delivery.models.entity;

/**
 * Документ отправления в ТК.
 *
 * @param code код документа внутри платформы (по нему скачивают)
 * @param name название для менеджера
 * @param ref  прямой URL (Возовоз, СДЭК) или UID документа в ТК (Деловые Линии); на фронт не отдаётся
 */
public record ShipmentDocument(String code, String name, String ref) {
}
