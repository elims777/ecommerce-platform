package ru.rfsnab.integrationservice.service.order;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Unmarshaller;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import ru.rfsnab.integrationservice.config.IntegrationProperties;
import ru.rfsnab.integrationservice.dto.OrderSyncRequest;
import ru.rfsnab.integrationservice.model.ImportLog;
import ru.rfsnab.integrationservice.model.ImportLog.ImportStatus;
import ru.rfsnab.integrationservice.model.commerceml.CmlDocument;
import ru.rfsnab.integrationservice.model.commerceml.CommerceInfo;
import ru.rfsnab.integrationservice.model.commerceml.RequisiteValue;
import ru.rfsnab.integrationservice.repository.ImportLogRepository;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Обработка статусов заказов от 1С.
 * 1С присылает XML (type=sale, mode=file) с документами,
 * содержащими наш orderId + номер 1С (externalId) + статус.
 * При mode=import мы парсим XML → обновляем order-service
 * через PATCH /api/v1/orders/{orderId}/1c-sync.
 * Формат XML от 1С:
 * <Документ>
 *   <Ид>orderId (наш UUID)</Ид>
 *   <Номер>РФ-000123 (номер в 1С)</Номер>
 *   <ЗначенияРеквизитов>
 *     <ЗначениеРеквизита>
 *       <Наименование>Статус заказа</Наименование>
 *       <Значение>PROCESSING</Значение>
 *     </ЗначениеРеквизита>
 *   </ЗначенияРеквизитов>
 * </Документ>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OrderStatusImportService {

    private static final String ORDERS_XML = "orders.xml";
    private static final String STATUS_REQUISITE_NAME = "Статус заказа";
    private static final String SYNC_URI = "/api/v1/orders/{orderId}/1c-sync";

    private final JAXBContext commerceMlJaxbContext;
    private final WebClient orderServiceClient;
    private final IntegrationProperties properties;
    private final ImportLogRepository importLogRepository;

    /**
     * Обрабатывает XML со статусами заказов от 1С.
     * Вызывается из контроллера при type=sale, mode=import.
     *
     * @return "success" или "failure\n{причина}" (протокол CommerceML)
     */
    public String processStatusUpdate() {
        LocalDateTime startedAt = LocalDateTime.now();
        Path exchangeDir = Path.of(properties.getCommerceml().getTempDir());
        Path ordersFile = exchangeDir.resolve(ORDERS_XML);

        if (!Files.exists(ordersFile)) {
            log.warn("Файл {} не найден — нет статусов для обработки", ORDERS_XML);
            return "success";
        }

        log.info("Обработка статусов заказов из {}", ORDERS_XML);

        try {
            CommerceInfo commerceInfo = parseXml(ordersFile);
            List<CmlDocument> documents = commerceInfo.getDocuments();

            if (documents == null || documents.isEmpty()) {
                log.info("Нет документов со статусами в {}", ORDERS_XML);
                saveImportLog("ORDER_STATUS", ImportStatus.SUCCESS, 0, 0, 0, null, startedAt);
                return "success";
            }

            int updated = 0;
            int failed = 0;
            List<String> errors = new ArrayList<>();

            for (CmlDocument doc : documents) {
                try {
                    syncOrderStatus(doc);
                    updated++;
                } catch (Exception e) {
                    failed++;
                    String error = String.format("Заказ %s: %s", doc.getId(), e.getMessage());
                    errors.add(error);
                    log.error("Ошибка обновления статуса заказа {}: {}", doc.getId(), e.getMessage());
                }
            }

            ImportStatus status = failed == 0 ? ImportStatus.SUCCESS
                    : (updated > 0 ? ImportStatus.PARTIAL : ImportStatus.FAILED);

            saveImportLog("ORDER_STATUS", status, documents.size(), updated, failed,
                    errors.isEmpty() ? null : String.join("; ", errors), startedAt);

            log.info("Обработка статусов завершена: updated={}, failed={}", updated, failed);

            return status == ImportStatus.FAILED
                    ? "failure\nВсе заказы завершились с ошибкой"
                    : "success";

        } catch (Exception e) {
            log.error("Критическая ошибка обработки статусов заказов", e);
            saveImportLog("ORDER_STATUS", ImportStatus.FAILED, 0, 0, 0, e.getMessage(), startedAt);
            return "failure\n" + e.getMessage();
        }
    }

    // ==================== Sync ====================

    /**
     * Обновляет статус одного заказа в order-service.
     * Извлекает orderId, номер 1С (externalId) и статус из документа.
     */
    private void syncOrderStatus(CmlDocument doc) {
        String orderId = doc.getId();
        String externalId = doc.getNumber(); // номер заказа в 1С
        String status = extractStatus(doc);

        if (orderId == null || orderId.isBlank()) {
            throw new IllegalStateException("Документ без Ид (orderId)");
        }
        if (status == null || status.isBlank()) {
            throw new IllegalStateException("Документ без статуса");
        }

        log.debug("Синхронизация заказа: orderId={}, externalId={}, status={}",
                orderId, externalId, status);

        OrderSyncRequest request = new OrderSyncRequest(externalId, status);

        try {
            orderServiceClient.patch()
                    .uri(SYNC_URI, orderId)
                    .bodyValue(request)
                    .retrieve()
                    .toBodilessEntity()
                    .block();
        } catch (WebClientResponseException e) {
            throw new RuntimeException("HTTP " + e.getStatusCode() + ": " + e.getResponseBodyAsString(), e);
        }
    }

    /**
     * Извлекает статус из ЗначенияРеквизитов документа.
     * Ищет реквизит с именем "Статус заказа".
     */
    private String extractStatus(CmlDocument doc) {
        if (doc.getRequisiteValues() == null) {
            return null;
        }
        return doc.getRequisiteValues().stream()
                .filter(r -> STATUS_REQUISITE_NAME.equals(r.getName()))
                .map(RequisiteValue::getValue)
                .findFirst()
                .orElse(null);
    }

    // ==================== XML Parsing ====================

    private CommerceInfo parseXml(Path xmlFile) {
        try {
            Unmarshaller unmarshaller = commerceMlJaxbContext.createUnmarshaller();
            return (CommerceInfo) unmarshaller.unmarshal(xmlFile.toFile());
        } catch (JAXBException e) {
            throw new IllegalStateException("Ошибка парсинга XML: " + xmlFile.getFileName(), e);
        }
    }

    // ==================== Logging ====================

    private void saveImportLog(String exchangeType, ImportStatus status,
                               int total, int updated, int failed,
                               String errorMessage, LocalDateTime startedAt) {
        try {
            LocalDateTime completedAt = LocalDateTime.now();
            long durationMs = Duration.between(startedAt, completedAt).toMillis();

            ImportLog logEntry = ImportLog.builder()
                    .exchangeType(exchangeType)
                    .status(status)
                    .totalReceived(total)
                    .updated(updated)
                    .failed(failed)
                    .durationMs(durationMs)
                    .errorMessage(errorMessage)
                    .startedAt(startedAt)
                    .completedAt(completedAt)
                    .build();
            importLogRepository.save(logEntry);
        } catch (Exception e) {
            log.error("Не удалось сохранить import_log: {}", e.getMessage());
        }
    }
}