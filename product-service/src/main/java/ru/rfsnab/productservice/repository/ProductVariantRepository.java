package ru.rfsnab.productservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.rfsnab.productservice.model.ProductVariant;

import java.util.List;
import java.util.Optional;

public interface ProductVariantRepository extends JpaRepository<ProductVariant, Long> {

    List<ProductVariant> findByProductId(Long productId);

    Optional<ProductVariant> findByExternalId(String externalId);

    List<ProductVariant> findByProductIdIn(List<Long> productIds);

    boolean existsByExternalId(String externalId);

    List<ProductVariant> findByExternalIdIn(List<String> externalIds);
}
