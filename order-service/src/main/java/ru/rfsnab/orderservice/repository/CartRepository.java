package ru.rfsnab.orderservice.repository;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;
import ru.rfsnab.orderservice.models.entity.Cart;

import java.util.Optional;
import java.util.UUID;

public interface CartRepository extends JpaRepository<Cart, UUID> {

    @EntityGraph(attributePaths = "items")
    Optional<Cart> findByUserId(Long userId);

    boolean existsByUserId(Long userId);

    @Transactional
    void deleteByUserId(Long userId);
}
