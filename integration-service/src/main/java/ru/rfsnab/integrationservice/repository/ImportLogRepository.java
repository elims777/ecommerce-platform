package ru.rfsnab.integrationservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.rfsnab.integrationservice.model.ImportLog;

import java.util.List;
import java.util.Optional;

@Repository
public interface ImportLogRepository extends JpaRepository<ImportLog, Long> {
    List<ImportLog> findTop20ByOrderByCreatedAtDesc();

    Optional<ImportLog> findFirstBySessionIdAndExchangeTypeOrderByCreatedAtDesc(String sessionId, String exchangeType);
}
