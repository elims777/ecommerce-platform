package ru.rfsnab.userservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.rfsnab.userservice.models.UserEntity;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<UserEntity, Long> {
    Optional<UserEntity> findByEmail(String email);
    Optional<UserEntity> findByUnsubscribeToken(String token);
    boolean existsByEmail(String email);
    boolean existsByPhone(String phone);
    List<UserEntity> findAllByLastLoginAtBeforeAndLastInactivityEmailAtIsNullOrLastInactivityEmailAtBefore(
            LocalDateTime loginThreshold, LocalDateTime emailThreshold);
}
