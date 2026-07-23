package ru.rfsnab.productservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.rfsnab.productservice.model.PriceListRequest;
import ru.rfsnab.productservice.model.PriceListStatus;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface PriceListRequestRepository extends JpaRepository<PriceListRequest, Long> {
    List<PriceListRequest> findByUserIdOrderByCreatedAtDesc(Long userId);

    boolean existsByUserIdAndStatus(Long userId, PriceListStatus status);

    List<PriceListRequest> findByStatusAndCreatedAtBefore(PriceListStatus status, LocalDateTime createdAtBefore);
}
