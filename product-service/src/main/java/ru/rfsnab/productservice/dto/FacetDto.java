package ru.rfsnab.productservice.dto;

import java.util.List;

/** Фасет каталога: свойство и его различные значения (без счётчиков). */
public record FacetDto(String name, List<String> values) {}
