package ru.rfsnab.orderservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.rfsnab.orderservice.models.entity.Recipient;
import ru.rfsnab.orderservice.models.entity.enums.CustomerType;

import java.util.List;
import java.util.Optional;

public interface RecipientRepository extends JpaRepository<Recipient, Long> {
    List<Recipient> findByUserIdAndCustomerType(Long userId, CustomerType customerType);

    Optional<Recipient> findByUserIdAndCustomerTypeAndIsDefaultTrue(Long userId, CustomerType customerType);
}
