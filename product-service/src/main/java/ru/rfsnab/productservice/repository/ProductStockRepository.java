package ru.rfsnab.productservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ru.rfsnab.productservice.model.ProductStock;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface ProductStockRepository extends JpaRepository<ProductStock, Long> {
    Optional<ProductStock> findByProductIdAndWarehouseId(Long productId, Long warehouseId);

    /** Сумма по активным складам: свои строки товара + строки склеенных с ним товаров. */
    @Query("""
            SELECT COALESCE(SUM(s.quantity), 0) FROM ProductStock s
            JOIN s.warehouse w JOIN s.product o
            WHERE w.active = true AND (o.id = :productId OR o.mergedIntoProductId = :productId)
            """)
    int sumActive(@Param("productId") Long productId);

    /** Строки для набора товаров и склеенных с ними — для агрегата getStocks (один запрос). */
    @Query("""
            SELECT s FROM ProductStock s
            JOIN FETCH s.warehouse w JOIN FETCH s.product o
            WHERE w.active = true AND (o.id IN :ids OR o.mergedIntoProductId IN :ids)
            """)
    List<ProductStock> findActiveForProducts(@Param("ids") Collection<Long> ids);

    @Query("SELECT s FROM ProductStock s JOIN FETCH s.warehouse WHERE s.product.id IN :ids")
    List<ProductStock> findByProductIdIn(@Param("ids") Collection<Long> ids);

    /** Пересчёт stock_quantity у всех товаров (при включении/выключении склада). */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE products p SET stock_quantity = COALESCE((
                SELECT SUM(s.quantity) FROM product_stocks s
                JOIN warehouses w ON w.id = s.warehouse_id
                JOIN products o ON o.id = s.product_id
                WHERE w.active = true AND (o.id = p.id OR o.merged_into_product_id = p.id)), 0)
            """, nativeQuery = true)
    int recalcAllTotals();
}
