package ru.rfsnab.productservice.repository.projection;

/**
 * Сырые агрегаты по таблице products — то, что реально считает БД одним проходом.
 * Производные величины (товары 1С, неактивные) вычисляет сервисный слой.
 */
public interface ProductStatsProjection {
    long getTotal();

    long getUniqueProducts();

    long getFtk();

    long getActive();
}
