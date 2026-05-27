package ru.rfsnab.userservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.rfsnab.userservice.models.LegalEntity;
import ru.rfsnab.userservice.models.enums.VerificationStatus;

import java.util.List;
import java.util.Optional;

/**
 * Репозиторий для работы с юридическими лицами.
 */
public interface LegalEntityRepository extends JpaRepository<LegalEntity, Long> {
    Optional<LegalEntity> findByEmail(String email);
    Optional<LegalEntity> findByInn(String inn);
    Optional<LegalEntity> findByEmailConfirmToken(String token);
    boolean existsByInn(String inn);
    boolean existsByEmail(String email);
    List<LegalEntity> findAllByVerificationStatus(VerificationStatus status);
}
