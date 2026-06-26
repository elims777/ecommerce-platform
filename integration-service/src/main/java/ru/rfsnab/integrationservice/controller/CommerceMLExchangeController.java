package ru.rfsnab.integrationservice.controller;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.rfsnab.integrationservice.config.IntegrationProperties;
import ru.rfsnab.integrationservice.model.ExchangeSession;
import ru.rfsnab.integrationservice.model.ExchangeSession.ExchangeType;
import ru.rfsnab.integrationservice.model.ImportLog;
import ru.rfsnab.integrationservice.model.ImportLog.ImportStatus;
import ru.rfsnab.integrationservice.dto.ImportLogDto;
import ru.rfsnab.integrationservice.repository.ImportLogRepository;
import ru.rfsnab.integrationservice.service.auth.ExchangeAuthService;
import ru.rfsnab.integrationservice.service.catalog.CatalogFileService;
import ru.rfsnab.integrationservice.service.catalog.CatalogImportService;
import ru.rfsnab.integrationservice.service.order.OrderExportService;
import ru.rfsnab.integrationservice.service.order.OrderExportService.OrderExportResult;
import ru.rfsnab.integrationservice.service.order.OrderStatusImportService;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Единый контроллер протокола обмена CommerceML с 1С.
 * URL: /1c-exchange?type={catalog|sale}&mode={checkauth|init|file|import|query|success}
 * Протокол:
 * 1С всегда инициатор. Формат ответа — plain text, разделитель строк "\n".
 * Первая строка — "success" или "failure".
 * Блок Каталог (type=catalog):
 *   checkauth → авторизация, возврат cookie
 *   init      → параметры обмена (zip, file_limit)
 *   file      → приём файлов (XML, картинки)
 *   import    → парсинг XML, отправка в product-service
 * Блок Заказы (type=sale):
 *   checkauth → авторизация
 *   init      → параметры
 *   query     → выгрузка заказов в формате CommerceML XML
 *   success   → подтверждение получения заказов
 *   file      → приём обновлённых заказов со статусами от 1С
 *   import    → обработка статусов → обновление в order-service
 */
@RestController
@RequestMapping("/1c-exchange")
@RequiredArgsConstructor
@Slf4j
public class CommerceMLExchangeController {

    private final ExchangeAuthService authService;
    private final CatalogFileService catalogFileService;
    private final CatalogImportService catalogImportService;
    private final OrderExportService orderExportService;
    private final OrderStatusImportService orderStatusImportService;
    private final IntegrationProperties properties;
    private final ImportLogRepository importLogRepository;

    /**
     * Единая точка входа протокола CommerceML.
     * 1С вызывает этот endpoint с параметрами type и mode.
     */
    @RequestMapping(produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> exchange(
            @RequestParam("type") String type,
            @RequestParam("mode") String mode,
            @RequestParam(value = "filename", required = false) String filename,
            @CookieValue(name = "exchange_session", required = false) String sessionCookie,
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            HttpServletRequest request) throws IOException {

        ExchangeType exchangeType = parseExchangeType(type);
        if (exchangeType == null) {
            return plainText("failure\nНеизвестный тип обмена: " + type);
        }

        // checkauth не требует cookie — это первый запрос
        if ("checkauth".equals(mode)) {
            return handleCheckAuth(authHeader, exchangeType);
        }

        // Все остальные запросы требуют валидную сессию
        Optional<ExchangeSession> session = authService.validateSession(sessionCookie);
        if (session.isEmpty()) {
            return plainText("failure\nСессия не найдена или истекла");
        }

        ExchangeSession sess = session.get();
        return switch (mode) {
            case "init" -> handleInit();
            case "file" -> handleFile(filename, sessionCookie, request);
            case "import" -> handleImport(exchangeType, sessionCookie);
            case "query" -> handleQuery(exchangeType, sessionCookie);
            case "success" -> handleSuccess(sessionCookie, sess);
            default -> plainText("failure\nНеизвестный режим: " + mode);
        };
    }

    /**
     * A. Начало сеанса — авторизация.
     * 1С отправляет Basic auth, мы проверяем и возвращаем cookie.
     * Ответ (3 строки):
     * success
     * exchange_session
     * {session_id}
     */
    private ResponseEntity<String> handleCheckAuth(String authHeader, ExchangeType exchangeType) {
        Optional<String> sessionId = authService.authenticate(authHeader, exchangeType);

        if (sessionId.isEmpty()) {
            return plainText("failure\nНеверный логин или пароль");
        }

        String cookieName = authService.getCookieName();
        String body = "success\n" + cookieName + "\n" + sessionId.get();

        return ResponseEntity.ok()
                .contentType(MediaType.valueOf("text/plain;charset=windows-1251"))
                .header("Set-Cookie", cookieName + "=" + sessionId.get() + "; Path=/")
                .body(body);
    }

    /**
     * B. Запрос параметров от сайта.
     * Сообщаем 1С: поддержка zip, лимит размера файла.
     * Ответ (2 строки):
     * zip=no
     * file_limit={bytes}
     */
    private ResponseEntity<String> handleInit() {
        long fileLimit = properties.getCommerceml().getFileLimit();
        return plainText("zip=no\nfile_limit=" + fileLimit);
    }

    /**
     * C. Приём файла от 1С.
     * Файл передаётся в теле запроса (не multipart!), имя файла в параметре filename.
     * Для каталога: import.xml, offers.xml, import_files/...jpg
     * Для заказов: orders.xml (обновлённые статусы)
     */
    private ResponseEntity<String> handleFile(String filename, String sessionId,
                                              HttpServletRequest request) throws IOException {
        if (filename == null || filename.isBlank()) {
            return plainText("failure\nНе указано имя файла");
        }

        catalogFileService.saveFile(filename, sessionId, request.getInputStream());
        return plainText("success");
    }

    /**
     * D. Импорт — обработка загруженных файлов.
     * Для каталога: парсинг import.xml/offers.xml → batch в product-service.
     * Для заказов: парсинг orders.xml со статусами → обновление в order-service.
     */
    private ResponseEntity<String> handleImport(ExchangeType exchangeType, String sessionId) {
        if (exchangeType == ExchangeType.CATALOG) {
            Path exchangeDir = Path.of(properties.getCommerceml().getTempDir());
            String result = catalogImportService.processImport(exchangeDir, sessionId);
            return plainText(result);
        } else {
            String result = orderStatusImportService.processStatusUpdate();
            return plainText(result);
        }
    }

    /**
     * E. Выгрузка заказов для 1С (type=sale, mode=query).
     * Формируем CommerceML XML из накопленных заказов и отдаём 1С.
     */
    private ResponseEntity<String> handleQuery(ExchangeType exchangeType, String sessionCookie) {
        if (exchangeType != ExchangeType.SALE) {
            return plainText("failure\nquery доступен только для type=sale");
        }

        OrderExportResult result = orderExportService.exportPendingOrders();
        saveOrderExportQueryLog(sessionCookie, result.count());
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_XML)
                .body(result.xml());
    }

    /**
     * F. Подтверждение успешного получения данных.
     * Для заказов (SALE): помечаем выгруженные заказы как переданные и обновляем лог.
     */
    private ResponseEntity<String> handleSuccess(String sessionCookie, ExchangeSession session) {
        if (session.getExchangeType() == ExchangeType.SALE) {
            int count = orderExportService.markOrdersAsExported();
            updateOrderExportSuccessLog(sessionCookie, count);
        }
        authService.completeSession(sessionCookie);
        return plainText("success");
    }

    /** Сохраняет запись ORDER_EXPORT при mode=query (заказы выгружены 1С). */
    private void saveOrderExportQueryLog(String sessionCookie, int count) {
        try {
            LocalDateTime now = LocalDateTime.now();
            ImportLog logEntry = ImportLog.builder()
                    .exchangeType("ORDER_EXPORT")
                    .sessionId(sessionCookie)
                    .status(ImportStatus.PARTIAL)
                    .totalReceived(count)
                    .created(count)
                    .startedAt(now)
                    .completedAt(now)
                    .durationMs(0L)
                    .build();
            importLogRepository.save(logEntry);
        } catch (Exception e) {
            // Ошибка лога не должна ломать ответ 1С
            log.error("Не удалось сохранить ORDER_EXPORT query лог: {}", e.getMessage());
        }
    }

    /**
     * Обновляет или создаёт запись ORDER_EXPORT при mode=success (1С подтвердила).
     * Ищет последнюю PARTIAL-запись по sessionId и обновляет её статус на SUCCESS.
     */
    private void updateOrderExportSuccessLog(String sessionCookie, int count) {
        try {
            LocalDateTime now = LocalDateTime.now();
            Optional<ImportLog> existing = importLogRepository
                    .findFirstBySessionIdAndExchangeTypeOrderByCreatedAtDesc(sessionCookie, "ORDER_EXPORT");
            if (existing.isPresent()) {
                ImportLog entry = existing.get();
                long durationMs = entry.getStartedAt() != null
                        ? Duration.between(entry.getStartedAt(), now).toMillis()
                        : 0L;
                entry.setStatus(ImportStatus.SUCCESS);
                entry.setUpdated(count);
                entry.setCompletedAt(now);
                entry.setDurationMs(durationMs);
                importLogRepository.save(entry);
            } else {
                ImportLog logEntry = ImportLog.builder()
                        .exchangeType("ORDER_EXPORT")
                        .sessionId(sessionCookie)
                        .status(ImportStatus.SUCCESS)
                        .totalReceived(count)
                        .updated(count)
                        .startedAt(now)
                        .completedAt(now)
                        .durationMs(0L)
                        .build();
                importLogRepository.save(logEntry);
            }
        } catch (Exception e) {
            log.error("Не удалось обновить ORDER_EXPORT success лог: {}", e.getMessage());
        }
    }

    /** Последние 20 записей лога обменов для admin-панели. */
    @GetMapping(value = "/logs", produces = "application/json")
    public List<ImportLogDto> getLogs() {
        return importLogRepository.findTop20ByOrderByCreatedAtDesc()
                .stream().map(ImportLogDto::from).toList();
    }

    // ===== Вспомогательные методы =====

    private ExchangeType parseExchangeType(String type) {
        return switch (type.toLowerCase()) {
            case "catalog" -> ExchangeType.CATALOG;
            case "sale" -> ExchangeType.SALE;
            default -> null;
        };
    }

    private ResponseEntity<String> plainText(String body) {
        return ResponseEntity.ok()
                .contentType(MediaType.valueOf("text/plain;charset=windows-1251"))
                .body(body);
    }
}