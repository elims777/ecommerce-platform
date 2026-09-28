package ru.rfsnab.userservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ru.rfsnab.userservice.models.UserEntity;
import ru.rfsnab.userservice.repository.projection.UserStatsProjection;

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
    long countByRoles_Name(String roleName);

    /**
     * Агрегаты по пользователям для сводки админки — один проход по таблице.
     * Администраторы (ROLE_ADMIN) исключены из подсчёта целиком.
     */
    @Query("""
            SELECT COUNT(u) AS total,
                   COALESCE(SUM(CASE WHEN u.emailVerified = true THEN 1 ELSE 0 END), 0) AS emailVerified,
                   COALESCE(SUM(CASE WHEN EXISTS (
                           SELECT 1 FROM UserLegalEntity ule
                           WHERE ule.user.id = u.id
                             AND ule.linkStatus = ru.rfsnab.userservice.models.enums.LinkStatus.CONFIRMED
                       ) THEN 1 ELSE 0 END), 0) AS withLegalEntity,
                   COALESCE(SUM(CASE WHEN u.active = true THEN 1 ELSE 0 END), 0) AS active,
                   COALESCE(SUM(CASE WHEN u.createdAt >= :newUserThreshold THEN 1 ELSE 0 END), 0) AS newLast30Days
            FROM UserEntity u
            WHERE NOT EXISTS (
                SELECT 1 FROM UserEntity admin JOIN admin.roles role
                WHERE admin.id = u.id AND role.name = 'ROLE_ADMIN'
            )
            """)
    UserStatsProjection fetchUserStats(@Param("newUserThreshold") LocalDateTime newUserThreshold);
}
