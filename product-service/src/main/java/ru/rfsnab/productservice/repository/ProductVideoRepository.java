package ru.rfsnab.productservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ru.rfsnab.productservice.model.ProductVideo;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductVideoRepository extends JpaRepository<ProductVideo, Long> {

    /**
     * Получить все видео товара
     * @param id товара
     * @return List<ProductVideo>
     */
    @Query("SELECT pv FROM ProductVideo pv WHERE pv.product.id = :productId ORDER BY pv.displayOrder ASC")
    List<ProductVideo> findAllByProduct(@Param("productId") Long id);

    /**
     * Получить главное видео товара
     * @param id товара
     * @return Optional<ProductVideo>
     */
    @Query("SELECT pv FROM ProductVideo pv WHERE pv.product.id = :productId AND pv.isPrimary = true")
    Optional<ProductVideo> findPrimaryByProduct(@Param("productId") Long id);

    /**
     * Сбросить флаг главных видео для товара
     * @param id товара
     */
    @Modifying
    @Query("UPDATE ProductVideo pv SET pv.isPrimary = false WHERE pv.product.id = :productId")
    void resetPrimaryForProduct(@Param("productId") Long id);

    /**
     * Удалить все видео товара
     * @param id товара
     */
    @Modifying
    @Query("DELETE FROM ProductVideo pv WHERE pv.product.id = :productId")
    void deleteByProduct(@Param("productId") Long id);
}
