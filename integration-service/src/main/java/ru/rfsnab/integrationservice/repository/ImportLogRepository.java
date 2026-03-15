package ru.rfsnab.integrationservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.rfsnab.integrationservice.model.ImportLog;

@Repository
public interface ImportLogRepository extends JpaRepository<ImportLog, Long> {
}
