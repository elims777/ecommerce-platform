package ru.rfsnab.integrationservice.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.rfsnab.integrationservice.dto.ImportLogDto;
import ru.rfsnab.integrationservice.repository.ImportLogRepository;

import java.util.List;

/**
 * Логи обменов для admin-панели.
 * Авторизация: ROLE_ADMIN проверяется на стороне gateway-service.
 */
@RestController
@RequestMapping("/api/v1/integration/logs")
@RequiredArgsConstructor
public class IntegrationLogController {

    private final ImportLogRepository importLogRepository;

    /** Последние 20 записей лога обменов. */
    @GetMapping(produces = "application/json")
    public List<ImportLogDto> getLogs() {
        return importLogRepository.findTop20ByOrderByCreatedAtDesc()
                .stream().map(ImportLogDto::from).toList();
    }
}
