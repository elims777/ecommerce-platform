package ru.rfsnab.integrationservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.rfsnab.integrationservice.model.ExchangeSession;

import java.util.Optional;

@Repository
public interface ExchangeSessionRepository extends JpaRepository<ExchangeSession, Long> {
    Optional<ExchangeSession> findBySessionId(String sessionId);
}
