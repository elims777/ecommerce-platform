package ru.rfsnab.integrationservice.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
 * Контроллер импорта каталога ФТК из XLS файла.
 * Авторизация: ROLE_ADMIN проверяется на стороне gateway-service.
 */
@RestController
@RequestMapping("/api/v1/integration/ftk")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "FTK Import", description = "Импорт каталога ФТК из XLS")
public class FtkImportController {

    private final FtkImportService ftkImportService;

    /**
     * POST /api/v1/integration/ftk/import
     * Принимает XLS-файл выгрузки ФТК, импортирует товары с вариантами и изображениями.
     */
    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Импорт каталога ФТК из XLS файла")
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

        log.info("ФТК импорт запущен: файл='{}', размер={} байт",
                originalFilename, file.getSize());

        try {
            FtkImportResult result = ftkImportService.importFromXls(file.getInputStream());

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("status", result.failed() == 0 ? "success" : "partial");
            response.put("totalProducts", result.totalProducts());
            response.put("created", result.created());
            response.put("updated", result.updated());
            response.put("failed", result.failed());
            response.put("imagesOk", result.imagesOk());
            response.put("imagesFailed", result.imagesFailed());

            log.info("ФТК импорт завершён: {}", response);
            return ResponseEntity.ok(response);

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
}
