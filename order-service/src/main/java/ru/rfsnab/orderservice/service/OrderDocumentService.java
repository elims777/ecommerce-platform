package ru.rfsnab.orderservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import ru.rfsnab.orderservice.exception.InvalidDocumentException;
import ru.rfsnab.orderservice.exception.InvalidOrderStateException;
import ru.rfsnab.orderservice.exception.OrderDocumentNotFoundException;
import ru.rfsnab.orderservice.kafka.OrderKafkaProducer;
import ru.rfsnab.orderservice.models.entity.Order;
import ru.rfsnab.orderservice.models.entity.OrderDocument;
import ru.rfsnab.orderservice.models.entity.enums.OrderDocumentType;
import ru.rfsnab.orderservice.repository.OrderDocumentRepository;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Управление документами заказа (счета, УПД, КП, сертификаты).
 * Загрузка доступна только админу, просмотр/скачивание — админу и владельцу заказа.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderDocumentService {

    private static final long MAX_FILE_SIZE_BYTES = 20L * 1024 * 1024;

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            "pdf", "jpg", "jpeg", "png", "xlsx", "docx");

    private final OrderDocumentRepository orderDocumentRepository;
    private final OrderDocumentStorage orderDocumentStorage;
    private final OrderService orderService;
    private final OrderKafkaProducer kafkaProducer;

    public record DownloadResult(ResponseInputStream<GetObjectResponse> stream, OrderDocument document) {
    }

    @Transactional
    public OrderDocument upload(UUID orderId, OrderDocumentType type, MultipartFile file, Long adminUserId) {
        validateFile(file);

        Order order = orderService.getOrder(orderId);

        String sanitizedName = sanitizeFileName(file.getOriginalFilename());
        String fileKey = "orders/" + orderId + "/" + UUID.randomUUID() + "_" + sanitizedName;

        OrderDocument document = OrderDocument.builder()
                .orderId(orderId)
                .type(type)
                .originalFileName(sanitizedName)
                .fileKey(fileKey)
                .contentType(file.getContentType())
                .sizeBytes(file.getSize())
                .uploadedAt(LocalDateTime.now())
                .uploadedByUserId(adminUserId)
                .build();

        // Сначала запись в БД, потом заливка в S3: при откате транзакции файл в бакет
        // ещё не попал. Обратный порядок оставлял бы файл-сироту в S3 без ссылки из БД.
        document = orderDocumentRepository.save(document);
        orderDocumentStorage.upload(file, fileKey);
        log.info("Документ '{}' ({}) загружен для заказа {}", sanitizedName, type, order.getOrderNumber());

        kafkaProducer.sendOrderDocumentAdded(order, type.getDisplayName(), sanitizedName);

        return document;
    }

    @Transactional(readOnly = true)
    public List<OrderDocument> listForOrder(UUID orderId) {
        orderService.getOrder(orderId);
        return orderDocumentRepository.findByOrderIdOrderByUploadedAtDesc(orderId);
    }

    @Transactional(readOnly = true)
    public List<OrderDocument> listForUser(UUID orderId, Long userId) {
        requireOwnedOrder(orderId, userId);
        return orderDocumentRepository.findByOrderIdOrderByUploadedAtDesc(orderId);
    }

    @Transactional(readOnly = true)
    public DownloadResult download(UUID orderId, UUID documentId) {
        orderService.getOrder(orderId);
        OrderDocument document = getDocumentOrThrow(documentId, orderId);
        return new DownloadResult(orderDocumentStorage.downloadStream(document.getFileKey()), document);
    }

    @Transactional(readOnly = true)
    public DownloadResult downloadForUser(UUID orderId, UUID documentId, Long userId) {
        requireOwnedOrder(orderId, userId);
        OrderDocument document = getDocumentOrThrow(documentId, orderId);
        return new DownloadResult(orderDocumentStorage.downloadStream(document.getFileKey()), document);
    }

    /**
     * Проверка владельца заказа для клиентских (не админских) файловых эндпоинтов.
     * getOrderByIdAndUser на чужом заказе бросает InvalidOrderStateException (400) —
     * здесь намеренно переводим её в 404, иначе сам факт "400, а не 404" косвенно
     * подтверждает клиенту, что такой orderId вообще существует (утечка через файловый API).
     */
    private void requireOwnedOrder(UUID orderId, Long userId) {
        try {
            orderService.getOrderByIdAndUser(orderId, userId);
        } catch (InvalidOrderStateException e) {
            throw new OrderDocumentNotFoundException("Заказ не найден: " + orderId);
        }
    }

    @Transactional
    public void delete(UUID orderId, UUID documentId) {
        orderService.getOrder(orderId);
        OrderDocument document = getDocumentOrThrow(documentId, orderId);

        // Сначала удаляем запись, потом объект в S3: при сбое останется мусорный файл
        // в бакете, а не битая ссылка в БД, ведущая к ошибке при скачивании.
        orderDocumentRepository.delete(document);
        orderDocumentStorage.delete(document.getFileKey());

        log.info("Документ {} удалён из заказа {}", document.getOriginalFileName(), orderId);
    }

    private OrderDocument getDocumentOrThrow(UUID documentId, UUID orderId) {
        return orderDocumentRepository.findByIdAndOrderId(documentId, orderId)
                .orElseThrow(() -> new OrderDocumentNotFoundException("Документ не найден: " + documentId));
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidDocumentException("Файл не может быть пустым");
        }
        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new InvalidDocumentException(
                    "Размер файла превышает максимально допустимый: " + (MAX_FILE_SIZE_BYTES / 1024 / 1024) + " MB");
        }

        String extension = extractExtension(file.getOriginalFilename());
        if (extension == null || !ALLOWED_EXTENSIONS.contains(extension)) {
            throw new InvalidDocumentException(
                    "Неподдерживаемый тип файла. Разрешены: " + ALLOWED_EXTENSIONS);
        }
    }

    private String extractExtension(String fileName) {
        if (fileName == null) {
            return null;
        }
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == fileName.length() - 1) {
            return null;
        }
        return fileName.substring(dotIndex + 1).toLowerCase();
    }

    /**
     * Убирает путь и недопустимые символы из имени файла, пришедшего извне.
     */
    private String sanitizeFileName(String fileName) {
        if (fileName == null) {
            return "file";
        }
        String baseName = fileName.replace('\\', '/');
        baseName = baseName.substring(baseName.lastIndexOf('/') + 1);
        return baseName.replaceAll("[^a-zA-Zа-яА-Я0-9._-]", "_");
    }
}
