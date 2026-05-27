package ru.rfsnab.orderservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.rfsnab.orderservice.models.entity.RecipientAddress;

import java.util.List;
import java.util.Optional;

public interface RecipientAddressRepository extends JpaRepository<RecipientAddress, Long> {
    List<RecipientAddress> findByRecipientId(Long recipientId);

    Optional<RecipientAddress> findByRecipientIdAndIsDefaultTrue(Long recipientId);
}
