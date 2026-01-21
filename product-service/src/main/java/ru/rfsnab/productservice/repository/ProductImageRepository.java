package ru.rfsnab.productservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ru.rfsnab.productservice.model.ProductImage;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductImageRepository extends JpaRepository<ProductImage, Long> {

    /**
     * Получить все изображения товара
     * @param id товара
     * @return List<ProductImage>
     */
    @Query("SELECT pi FROM ProductImage pi WHERE pi.product.id = :productId ORDER BY pi.displayOrder ASC")
    List<ProductImage> findAllByProduct(@Param("productId") Long id);

    /**
     * Получить главное изображение товара
     * @param id товара
     * @return Optional<ProductImage>
     */
    @Query("SELECT pi FROM ProductImage pi WHERE pi.product.id = :productId AND pi.isPrimary = true")
    Optional<ProductImage> findPrimaryByProduct(@Param("productId") Long id);

    /**
     * Сбросить главное изображение товара
     * @param id товара
     */
    @Modifying
    @Query("UPDATE ProductImage pi SET pi.isPrimary = false WHERE pi.product.id = :productId")
    void resetPrimaryForProduct(@Param("productId") Long id);

    /**
     * Удалить все изображения товара
     * @param id товара
     */
    @Modifying
    @Query("DELETE FROM ProductImage pi WHERE pi.product.id = :productId")
    void deleteAllByProduct(@Param("productId") Long id);
}
