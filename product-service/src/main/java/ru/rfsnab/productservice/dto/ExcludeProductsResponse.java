package ru.rfsnab.productservice.dto;

/**
 * Результат удаления товаров по externalId: общее число удалённых строк.
 */
public record ExcludeProductsResponse(int deleted) {
}
