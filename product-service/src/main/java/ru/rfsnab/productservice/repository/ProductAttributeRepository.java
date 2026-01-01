package ru.rfsnab.productservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ru.rfsnab.productservice.model.ProductAttribute;

import java.util.List;

@Repository
public interface ProductAttributeRepository extends JpaRepository<ProductAttribute, Long> {

    /**
     * Получить все атрибуты товара
     * @param id товара
     * @return List<ProductAttribute>
     */
    @Query("SELECT pa FROM ProductAttribute pa WHERE pa.product.id = :productId")
    List<ProductAttribute> findAllByProduct(@Param("productId") Long id);

    /**
     * Получить атрибуты по имени
     * @param name имя
     * @return List<ProductAttribute>
     */
    @Query("SELECT pa FROM ProductAttribute pa WHERE pa.attributeName = :name")
    List<ProductAttribute> findByName(@Param("name") String name);

    /**
     * Удалить все атрибуты товара
     * @param id товара
     */
    @Modifying
    @Query("DELETE FROM ProductAttribute pa WHERE pa.product.id = :productId")
    void deleteAllByProduct(@Param("productId") Long id);
}
