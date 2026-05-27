package ru.rfsnab.userservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.rfsnab.userservice.models.LegalEntityAddress;

import java.util.List;
import java.util.Optional;

/**
 * Репозиторий для работы с адресами доставки юридических лиц.
 */
public interface LegalEntityAddressRepository extends JpaRepository<LegalEntityAddress, Long> {
    List<LegalEntityAddress> findAllByLegalEntityId(Long legalEntityId);
    Optional<LegalEntityAddress> findByIdAndLegalEntityId(Long id, Long legalEntityId);
}
