package ru.rfsnab.notificationservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.rfsnab.notificationservice.models.EmailVerificationToken;

import java.util.Optional;

@Repository
public interface EmailVerificationTokenRepository extends JpaRepository<EmailVerificationToken, Long> {

    Optional<EmailVerificationToken> findByToken(String token);
    Optional<EmailVerificationToken> findByUserId(Long userId);
}
