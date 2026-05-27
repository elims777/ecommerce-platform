package ru.rfsnab.userservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.rfsnab.userservice.models.LegalEntityBankAccount;

import java.util.List;
import java.util.Optional;

/**
 * Репозиторий для работы с банковскими счетами юридических лиц.
 */
public interface LegalEntityBankAccountRepository extends JpaRepository<LegalEntityBankAccount, Long> {
    List<LegalEntityBankAccount> findAllByLegalEntityId(Long legalEntityId);
    Optional<LegalEntityBankAccount> findByIdAndLegalEntityId(Long id, Long legalEntityId);
}
