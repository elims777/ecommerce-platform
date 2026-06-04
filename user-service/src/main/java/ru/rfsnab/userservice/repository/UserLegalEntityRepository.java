package ru.rfsnab.userservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.rfsnab.userservice.models.UserLegalEntity;
import ru.rfsnab.userservice.models.UserLegalEntityId;
import ru.rfsnab.userservice.models.enums.LinkStatus;

import java.util.List;
import java.util.Optional;

public interface UserLegalEntityRepository extends JpaRepository<UserLegalEntity, UserLegalEntityId> {
    @Query("SELECT ule FROM UserLegalEntity ule JOIN FETCH ule.legalEntity JOIN FETCH ule.user WHERE ule.user.id = :userId AND ule.linkStatus = :status")
    List<UserLegalEntity> findAllByUserIdAndLinkStatus(@Param("userId") Long userId, @Param("status") LinkStatus status);

    @Query("SELECT ule FROM UserLegalEntity ule JOIN FETCH ule.legalEntity JOIN FETCH ule.user WHERE ule.user.id = :userId")
    List<UserLegalEntity> findAllByUserId(@Param("userId") Long userId);

    @Query("SELECT ule FROM UserLegalEntity ule JOIN FETCH ule.legalEntity JOIN FETCH ule.user WHERE ule.linkToken = :token")
    Optional<UserLegalEntity> findByLinkToken(@Param("token") String token);

    boolean existsByUserIdAndLegalEntityId(Long userId, Long legalEntityId);

    @Query("SELECT ule FROM UserLegalEntity ule JOIN FETCH ule.legalEntity JOIN FETCH ule.user WHERE ule.user.id = :userId AND ule.legalEntity.id = :legalEntityId")
    Optional<UserLegalEntity> findByUserIdAndLegalEntityId(@Param("userId") Long userId, @Param("legalEntityId") Long legalEntityId);
}
