package ru.rfsnab.integrationservice.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import ru.rfsnab.integrationservice.service.ftk.FtkImportService;
import ru.rfsnab.integrationservice.service.ftk.FtkImportService.FtkImportResult;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Контроллер импорта каталога ФТК.
 * Авторизация: ROLE_ADMIN проверяется на стороне gateway-service.
 */
@RestController
@RequestMapping("/api/v1/integration/ftk")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "FTK Import", description = "Импорт каталога ФТК")
public class FtkImportController {

    private final FtkImportService ftkImportService;

    // ──────────────────────────────────────────────────────────────
    // XML импорт с FTP (актуальный)
    // ──────────────────────────────────────────────────────────────

    /**
     * POST /api/v1/integration/ftk/import-xml
     * Скачивает XML-файлы с FTP ФТК и импортирует каталог.
     */
    @PostMapping("/import-xml")
    @Operation(summary = "Импорт каталога ФТК с FTP (XML CommerceML 3.1)")
    public ResponseEntity<Map<String, Object>> importXml() {
        log.info("ФТК XML импорт запущен вручную");
        return runXmlImport();
    }

    /** Автоматический запуск ежедневно в 04:00 МСК (на час позже обновления данных у ФТК). */
    @Scheduled(cron = "0 0 4 * * *")
    public void scheduledXmlImport() {
        log.info("ФТК XML импорт запущен по расписанию (04:00 МСК)");
        runXmlImport();
    }

    private ResponseEntity<Map<String, Object>> runXmlImport() {
        try {
            FtkImportResult result = ftkImportService.importFromFtp();
            return ResponseEntity.ok(buildResponse(result));
        } catch (Exception e) {
            log.error("Ошибка XML импорта ФТК", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Ошибка импорта: " + e.getMessage()));
        }
    }

    // ──────────────────────────────────────────────────────────────
    // XLS импорт (устаревший, сохранён для совместимости)
    // ──────────────────────────────────────────────────────────────

    /**
     * POST /api/v1/integration/ftk/import
     * @deprecated Используй /import-xml. XLS делался для другого клиента ФТК.
     */
    @Deprecated
    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Импорт каталога ФТК из XLS файла (устарел)")
    public ResponseEntity<Map<String, Object>> importXls(
            @RequestParam("file") MultipartFile file
    ) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Файл не передан"));
        }
        String originalFilename = file.getOriginalFilename();
        if (originalFilename != null && !originalFilename.toLowerCase().endsWith(".xls")) {
            return ResponseEntity.badRequest().body(Map.of("error", "Ожидается файл .xls (HSSF формат)"));
        }
        log.info("ФТК XLS импорт запущен: файл='{}', размер={} байт", originalFilename, file.getSize());
        try {
            FtkImportResult result = ftkImportService.importFromXls(file.getInputStream());
            return ResponseEntity.ok(buildResponse(result));
        } catch (IOException e) {
            log.error("Ошибка чтения XLS файла ФТК: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Ошибка чтения XLS: " + e.getMessage()));
        } catch (Exception e) {
            log.error("Непредвиденная ошибка импорта ФТК", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Внутренняя ошибка: " + e.getMessage()));
        }
    }

    // ──────────────────────────────────────────────────────────────

    private Map<String, Object> buildResponse(FtkImportResult result) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", result.failed() == 0 ? "success" : "partial");
        response.put("totalProducts", result.totalProducts());
        response.put("created", result.created());
        response.put("updated", result.updated());
        response.put("failed", result.failed());
        response.put("imagesOk", result.imagesOk());
        response.put("imagesFailed", result.imagesFailed());
        return response;
    }
}
