package ru.rfsnab.orderservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.rfsnab.orderservice.models.entity.OrderDocument;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrderDocumentRepository extends JpaRepository<OrderDocument, UUID> {

    List<OrderDocument> findByOrderIdOrderByUploadedAtDesc(UUID orderId);

    Optional<OrderDocument> findByIdAndOrderId(UUID id, UUID orderId);
}
