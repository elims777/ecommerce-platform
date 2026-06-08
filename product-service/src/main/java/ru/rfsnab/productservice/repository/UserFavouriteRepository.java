package ru.rfsnab.productservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.rfsnab.productservice.model.UserFavourite;

import java.util.List;
import java.util.Optional;

public interface UserFavouriteRepository extends JpaRepository<UserFavourite, Long> {

    List<UserFavourite> findByUserEmail(String userEmail);

    Optional<UserFavourite> findByUserEmailAndProductId(String userEmail, Long productId);

    boolean existsByUserEmailAndProductId(String userEmail, Long productId);

    void deleteByUserEmailAndProductId(String userEmail, Long productId);
}
