package ru.rfsnab.productservice.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ru.rfsnab.productservice.model.Product;
import ru.rfsnab.productservice.repository.projection.ProductStatsProjection;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long>, JpaSpecificationExecutor<Product> {
    long countByCategoryId(Long categoryId);

    Optional<Product> findBySlug(String slug);

    boolean existsBySlug(String slug);

    Page<Product> findByCategoryId(Long categoryId, Pageable pageable);

    @Query("SELECT p FROM Product p WHERE p.isActive = true ORDER BY p.createdAt DESC")
    List<Product> findAllActive();

    @Query("""
            SELECT p FROM Product p
            WHERE (LOWER(p.name) LIKE LOWER(CONCAT('%', :query, '%'))
                OR LOWER(p.sku) LIKE LOWER(CONCAT('%', :query, '%'))
                OR LOWER(p.externalCode) LIKE LOWER(CONCAT('%', :query, '%'))
                OR LOWER(p.externalId) LIKE LOWER(CONCAT('%', :query, '%')))
              AND p.isActive = true AND p.isVariantChild = false
            """)
    Page<Product> searchByNameOrArticle(@Param("query") String query, Pageable pageable);

    /**
     * Кандидаты в родители для админки: в отличие от публичного поиска НЕ фильтрует по isActive
     * (свежеимпортированные товары неактивны). Сначала товары из категории categoryId,
     * затем те, у кого имя/артикул начинается с запроса, затем по имени.
     */
    @Query(value = """
            SELECT p FROM Product p
            LEFT JOIN p.category c
            WHERE p.isVariantChild = false
              AND p.id NOT IN :excludeIds
              AND (LOWER(p.name) LIKE LOWER(CONCAT('%', :query, '%'))
                OR LOWER(p.sku) LIKE LOWER(CONCAT('%', :query, '%'))
                OR LOWER(p.externalCode) LIKE LOWER(CONCAT('%', :query, '%')))
            ORDER BY
              CASE WHEN c.id = :categoryId THEN 0 ELSE 1 END,
              CASE WHEN LOWER(p.name) LIKE LOWER(CONCAT(:query, '%'))
                     OR LOWER(p.sku) LIKE LOWER(CONCAT(:query, '%')) THEN 0 ELSE 1 END,
              p.name
            """,
            countQuery = """
            SELECT COUNT(p) FROM Product p
            WHERE p.isVariantChild = false
              AND p.id NOT IN :excludeIds
              AND (LOWER(p.name) LIKE LOWER(CONCAT('%', :query, '%'))
                OR LOWER(p.sku) LIKE LOWER(CONCAT('%', :query, '%'))
                OR LOWER(p.externalCode) LIKE LOWER(CONCAT('%', :query, '%')))
            """)
    Page<Product> findParentCandidates(@Param("query") String query,
                                       @Param("categoryId") Long categoryId,
                                       @Param("excludeIds") List<Long> excludeIds,
                                       Pageable pageable);

    @Query("SELECT p FROM Product p WHERE p.isFeatured = true AND p.isActive = true")
    List<Product> findFeatured();

    Page<Product> findByCategoryIdAndIsActiveTrue(Long categoryId, Pageable pageable);

    Page<Product> findByIsActiveTrue(Pageable pageable);

    Optional<Product> findByExternalId(String externalId);

    List<Product> findByExternalIdIn(List<String> externalIds);

    /** Товары для YML-фида и sitemap: активные родительские карточки. Lazy-связи не трогаем. */
    @Query("SELECT p FROM Product p WHERE p.isActive = true AND p.isVariantChild = false ORDER BY p.id")
    List<Product> findAllForFeed();

    @Query("SELECT p.slug FROM Product p")
    List<String> findAllSlugs();

    @Query("SELECT p FROM Product p LEFT JOIN FETCH p.attributes WHERE p.parentProductId = :parentId AND p.isActive = true ORDER BY p.name ASC")
    List<Product> findChildrenWithAttributes(@Param("parentId") Long parentId);

    @Query("SELECT DISTINCT p.parentProductId FROM Product p WHERE p.parentProductId IN :ids AND p.isActive = true")
    List<Long> findParentIdsWithActiveChildren(@Param("ids") List<Long> ids);

    List<Product> findByParentProductId(Long parentProductId);

    boolean existsByParentProductId(Long parentProductId);

    /** Записи Факела с данным артикулом, у которых нет вариантов (склеиваемые). */
    @Query("""
            SELECT p FROM Product p
            WHERE p.sku = :sku AND p.source = 'FTK'
              AND NOT EXISTS (SELECT 1 FROM Product c WHERE c.parentProductId = p.id)
            """)
    List<Product> findMergeableFtkBySku(@Param("sku") String sku);

    /** Несклеенные и не заблокированные товары 1С с данным артикулом. */
    @Query("""
            SELECT p FROM Product p
            WHERE p.sku = :sku AND p.source = 'INTERNAL' AND p.externalId IS NOT NULL
              AND p.mergedIntoProductId IS NULL AND p.mergeLocked = false
            """)
    List<Product> findMergeableOneCBySku(@Param("sku") String sku);

    /** Склеенные с данными товарами записи 1С — для oneCExternalId и блока «Связанные товары». */
    List<Product> findByMergedIntoProductIdInOrderByIdAsc(Collection<Long> mainIds);

    @Query("""
            SELECT p FROM Product p
            WHERE p.source = 'INTERNAL' AND p.externalId IS NOT NULL AND p.mergedIntoProductId IS NULL
              AND (LOWER(p.name) LIKE LOWER(CONCAT('%', :query, '%'))
                OR LOWER(p.sku) LIKE LOWER(CONCAT('%', :query, '%')))
            """)
    Page<Product> findMergeCandidates(@Param("query") String query, Pageable pageable);

    @Query("""
            SELECT p FROM Product p
            WHERE p.source = 'INTERNAL' AND p.externalId IS NOT NULL
              AND p.mergedIntoProductId IS NULL AND p.isVariantChild = false
            """)
    Page<Product> findUnmatchedOneC(Pageable pageable);

    Page<Product> findByIsActiveTrueAndIsVariantChildFalse(Pageable pageable);

    Page<Product> findByCategoryIdAndIsActiveTrueAndIsVariantChildFalse(Long categoryId, Pageable pageable);

    Page<Product> findByCategoryIdInAndIsActiveTrueAndIsVariantChildFalse(List<Long> categoryIds, Pageable pageable);

    Page<Product> findByCategoryIdInAndIsActiveTrue(List<Long> categoryIds, Pageable pageable);

    Page<Product> findByIsActive(Boolean isActive, Pageable pageable);

    Page<Product> findByIsFeaturedTrueAndIsActiveTrueAndIsVariantChildFalse(Pageable pageable);

    Page<Product> findByCategoryIdAndIsActive(Long categoryId, Boolean isActive, Pageable pageable);

    Page<Product> findByIsVariantChildFalse(Pageable pageable);

    Page<Product> findByCategoryIdAndIsVariantChildFalse(Long categoryId, Pageable pageable);

    @Query("""
            SELECT DISTINCT p FROM Product p LEFT JOIN FETCH p.attributes
            WHERE p.parentProductId IN :parentIds
            ORDER BY p.parentProductId ASC, p.displayOrder ASC
            """)
    List<Product> findByParentProductIdInOrderByParentProductIdAscDisplayOrderAsc(@Param("parentIds") List<Long> parentIds);

    @Query("""
            SELECT COUNT(p) FROM Product p
            WHERE p.isActive = true AND p.isVariantChild = false
            AND (p.stockQuantity + COALESCE(
                (SELECT SUM(c.stockQuantity) FROM Product c
                 WHERE c.parentProductId = p.id AND c.isActive = true), 0)) > 0
            """)
    long countAvailableProducts();

    /**
     * Админский фильтр по активности: семья попадает в выдачу, если условию отвечает
     * сам родитель ИЛИ любой его вариант. Иначе неактивный вариант под активным
     * родителем не виден ни под одним фильтром.
     */
    @Query("""
            SELECT p FROM Product p
            WHERE p.isVariantChild = false
              AND (p.isActive = :isActive
                   OR EXISTS (SELECT 1 FROM Product c
                              WHERE c.parentProductId = p.id AND c.isActive = :isActive))
            """)
    Page<Product> findFamiliesByActive(@Param("isActive") Boolean isActive, Pageable pageable);

    @Query("""
            SELECT p FROM Product p
            WHERE p.isVariantChild = false
              AND p.category.id = :categoryId
              AND (p.isActive = :isActive
                   OR EXISTS (SELECT 1 FROM Product c
                              WHERE c.parentProductId = p.id AND c.isActive = :isActive))
            """)
    Page<Product> findFamiliesByCategoryAndActive(@Param("categoryId") Long categoryId,
                                                  @Param("isActive") Boolean isActive,
                                                  Pageable pageable);

    /**
     * Агрегаты каталога для сводки админки — один проход по таблице.
     * Товары 1С и неактивные выводятся вычитанием в сервисном слое.
     */
    @Query("""
            SELECT COUNT(p) AS total,
                   COALESCE(SUM(CASE WHEN p.isVariantChild = false THEN 1 ELSE 0 END), 0) AS uniqueProducts,
                   COALESCE(SUM(CASE WHEN p.source = 'FTK' THEN 1 ELSE 0 END), 0) AS ftk,
                   COALESCE(SUM(CASE WHEN p.isActive = true THEN 1 ELSE 0 END), 0) AS active
            FROM Product p
            """)
    ProductStatsProjection fetchProductStats();
}
