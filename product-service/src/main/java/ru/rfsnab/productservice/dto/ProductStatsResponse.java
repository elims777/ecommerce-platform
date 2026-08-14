package ru.rfsnab.productservice.dto;

import java.io.Serializable;

/**
 * Сводка по каталогу для админки.
 * Serializable — значение кладётся в Redis-кэш.
 *
 * @param uniqueProducts товары без учёта дочерних вариантов
 * @param total          все записи каталога, включая варианты
 * @param fromOneC       товары из 1С (source != FTK)
 * @param fromFtk        товары из ФТК
 * @param active         активные
 * @param inactive       неактивные
 */
public record ProductStatsResponse(
        long uniqueProducts,
        long total,
        long fromOneC,
        long fromFtk,
        long active,
        long inactive
) implements Serializable {
}
