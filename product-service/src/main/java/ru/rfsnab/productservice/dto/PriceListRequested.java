package ru.rfsnab.productservice.dto;

/**
 * Kafka-событие: запрошена генерация прайс-листа.
 */
public record PriceListRequested(Long requestId) {
}
