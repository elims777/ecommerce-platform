package ru.rfsnab.orderservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.rfsnab.orderservice.models.entity.Cart;

import java.util.Optional;
import java.util.UUID;

public interface CartRepository extends JpaRepository<Cart, UUID> {
    Optional<Cart> findByUserId(Long userId);

    boolean existsByUserId(Long userId);

    void deleteByUserId(Long userId);
}
