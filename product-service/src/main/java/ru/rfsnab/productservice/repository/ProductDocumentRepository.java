package ru.rfsnab.productservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.rfsnab.productservice.model.ProductDocument;

import java.util.List;

public interface ProductDocumentRepository extends JpaRepository<ProductDocument, Long> {
    List<ProductDocument> findByProductId(Long productId);
}
