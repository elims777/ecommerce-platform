package ru.rfsnab.integrationservice.controller;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.rfsnab.integrationservice.config.IntegrationProperties;
import ru.rfsnab.integrationservice.model.ExchangeSession;
import ru.rfsnab.integrationservice.model.ExchangeSession.ExchangeType;
import ru.rfsnab.integrationservice.service.auth.ExchangeAuthService;
import ru.rfsnab.integrationservice.service.catalog.CatalogFileService;
import ru.rfsnab.integrationservice.service.catalog.CatalogImportService;
import ru.rfsnab.integrationservice.service.order.OrderExportService;
import ru.rfsnab.integrationservice.service.order.OrderStatusImportService;

import java.io.IOException;
import java.nio.file.Path;
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
public class CommerceMLExchangeController {

    private final ExchangeAuthService authService;
    private final CatalogFileService catalogFileService;
    private final CatalogImportService catalogImportService;
    private final OrderExportService orderExportService;
    private final OrderStatusImportService orderStatusImportService;
    private final IntegrationProperties properties;

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

        return switch (mode) {
            case "init" -> handleInit();
            case "file" -> handleFile(exchangeType, filename, request);
            case "import" -> handleImport(exchangeType, sessionCookie);
            case "query" -> handleQuery(exchangeType);
            case "success" -> handleSuccess(sessionCookie);
            default -> plainText("failure\nНеизвестный режим: " + mode);
        };
    }

    /**
     * A. Начало сеанса — авторизация.
     * 1С отправляет Basic auth, мы проверяем и возвращаем cookie.
     *
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
                .contentType(MediaType.TEXT_PLAIN)
                .header("Set-Cookie", cookieName + "=" + sessionId.get() + "; Path=/")
                .body(body);
    }

    /**
     * B. Запрос параметров от сайта.
     * Сообщаем 1С: поддержка zip, лимит размера файла.
     *
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
    private ResponseEntity<String> handleFile(ExchangeType exchangeType, String filename,
                                              HttpServletRequest request) throws IOException {
        if (filename == null || filename.isBlank()) {
            return plainText("failure\nНе указано имя файла");
        }

        catalogFileService.saveFile(filename, request.getInputStream());
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
    private ResponseEntity<String> handleQuery(ExchangeType exchangeType) {
        if (exchangeType != ExchangeType.SALE) {
            return plainText("failure\nquery доступен только для type=sale");
        }

        String ordersXml = orderExportService.exportPendingOrders();
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_XML)
                .body(ordersXml);
    }

    /**
     * F. Подтверждение успешного получения данных.
     * Для заказов: помечаем выгруженные заказы как переданные.
     */
    private ResponseEntity<String> handleSuccess(String sessionCookie) {
        orderExportService.markOrdersAsExported();
        authService.completeSession(sessionCookie);
        return plainText("success");
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
                .contentType(MediaType.TEXT_PLAIN)
                .body(body);
    }
}