package ru.rfsnab.productservice.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ru.rfsnab.productservice.model.Product;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {
    long countByCategoryId(Long categoryId);

    Optional<Product> findBySlug(String slug);

    boolean existsBySlug(String slug);

    Page<Product> findByCategoryId(Long categoryId, Pageable pageable);

    @Query("SELECT p FROM Product p WHERE p.isActive = true ORDER BY p.createdAt DESC")
    List<Product> findAllActive();

    @Query("SELECT p FROM Product p WHERE LOWER(p.name) LIKE LOWER(CONCAT('%', :query, '%')) AND p.isActive = true")
    List<Product> searchByName(@Param("query") String query);

    @Query("SELECT p FROM Product p WHERE p.isFeatured = true AND p.isActive = true")
    List<Product> findFeatured();

    Page<Product> findByCategoryIdAndIsActiveTrue(Long categoryId, Pageable pageable);

    Page<Product> findByIsActiveTrue(Pageable pageable);

    Optional<Product> findByExternalId(String externalId);

    List<Product> findByExternalIdIn(List<String> externalIds);

    @Query("SELECT p.slug FROM Product p")
    List<String> findAllSlugs();

    @Query("SELECT p FROM Product p LEFT JOIN FETCH p.attributes WHERE p.parentProductId = :parentId AND p.isActive = true ORDER BY p.name ASC")
    List<Product> findChildrenWithAttributes(@Param("parentId") Long parentId);

    @Query("SELECT DISTINCT p.parentProductId FROM Product p WHERE p.parentProductId IN :ids AND p.isActive = true")
    List<Long> findParentIdsWithActiveChildren(@Param("ids") List<Long> ids);

    List<Product> findByParentProductId(Long parentProductId);

    Page<Product> findByIsActiveTrueAndIsVariantChildFalse(Pageable pageable);

    Page<Product> findByCategoryIdAndIsActiveTrueAndIsVariantChildFalse(Long categoryId, Pageable pageable);

    Page<Product> findByCategoryIdInAndIsActiveTrueAndIsVariantChildFalse(List<Long> categoryIds, Pageable pageable);

    Page<Product> findByIsActive(Boolean isActive, Pageable pageable);

    Page<Product> findByCategoryIdAndIsActive(Long categoryId, Boolean isActive, Pageable pageable);

    @Query("""
            SELECT COUNT(p) FROM Product p
            WHERE p.isActive = true AND p.isVariantChild = false
            AND (p.stockQuantity + COALESCE(
                (SELECT SUM(c.stockQuantity) FROM Product c
                 WHERE c.parentProductId = p.id AND c.isActive = true), 0)) > 0
            """)
    long countAvailableProducts();
}
