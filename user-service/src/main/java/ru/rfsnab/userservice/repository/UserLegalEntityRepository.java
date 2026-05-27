package ru.rfsnab.userservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.rfsnab.userservice.models.UserLegalEntity;
import ru.rfsnab.userservice.models.UserLegalEntityId;
import ru.rfsnab.userservice.models.enums.LinkStatus;

import java.util.List;
import java.util.Optional;

/**
 * Репозиторий для работы со связями физических пользователей и юридических лиц.
 */
public interface UserLegalEntityRepository extends JpaRepository<UserLegalEntity, UserLegalEntityId> {
    List<UserLegalEntity> findAllByUserIdAndLinkStatus(Long userId, LinkStatus status);
    List<UserLegalEntity> findAllByUserId(Long userId);
    Optional<UserLegalEntity> findByLinkToken(String token);
    boolean existsByUserIdAndLegalEntityId(Long userId, Long legalEntityId);
    Optional<UserLegalEntity> findByUserIdAndLegalEntityId(Long userId, Long legalEntityId);
}
