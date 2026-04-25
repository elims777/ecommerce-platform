package ru.rfsnab.orderservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.rfsnab.orderservice.models.entity.Recipient;

import java.util.List;
import java.util.Optional;

public interface RecipientRepository extends JpaRepository<Recipient, Long> {
    List<Recipient> findByUserId(Long userId);

    Optional<Recipient> findByUserIdAndIsDefaultTrue(Long userId);
}
