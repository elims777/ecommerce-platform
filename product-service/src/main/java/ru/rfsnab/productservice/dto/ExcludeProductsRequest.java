package ru.rfsnab.productservice.dto;

import java.util.List;

/**
 * Запрос на удаление товаров по externalId (товары ФТК «Распродажа»).
 */
public record ExcludeProductsRequest(List<String> externalIds) {
}
