package ru.rfsnab.orderservice.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import ru.rfsnab.orderservice.exception.InvalidDocumentException;
import ru.rfsnab.orderservice.exception.InvalidOrderStateException;
import ru.rfsnab.orderservice.exception.OrderDocumentNotFoundException;
import ru.rfsnab.orderservice.kafka.OrderKafkaProducer;
import ru.rfsnab.orderservice.models.entity.Order;
import ru.rfsnab.orderservice.models.entity.OrderDocument;
import ru.rfsnab.orderservice.models.entity.enums.OrderDocumentType;
import ru.rfsnab.orderservice.repository.OrderDocumentRepository;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrderDocumentService")
class OrderDocumentServiceTest {

    @Mock
    private OrderDocumentRepository orderDocumentRepository;

    @Mock
    private OrderDocumentStorage orderDocumentStorage;

    @Mock
    private OrderService orderService;

    @Mock
    private OrderKafkaProducer kafkaProducer;

    private OrderDocumentService orderDocumentService;

    private static final UUID ORDER_ID = UUID.randomUUID();
    private static final Long ADMIN_USER_ID = 1L;
    private static final Long OWNER_USER_ID = 42L;
    private static final Long OTHER_USER_ID = 99L;

    @BeforeEach
    void setUp() {
        orderDocumentService = new OrderDocumentService(
                orderDocumentRepository, orderDocumentStorage, orderService, kafkaProducer);
    }

    private Order buildOrder() {
        return Order.builder()
                .id(ORDER_ID)
                .userId(OWNER_USER_ID)
                .orderNumber("RF-00001")
                .totalAmount(BigDecimal.TEN)
                .customerEmail("client@company.ru")
                .build();
    }

    @Nested
    @DisplayName("upload()")
    class UploadTests {

        @Test
        @DisplayName("отклоняет файл неразрешённого типа")
        void shouldRejectDisallowedFileType() {
            MockMultipartFile file = new MockMultipartFile(
                    "file", "malware.exe", "application/octet-stream", "content".getBytes());

            assertThatThrownBy(() -> orderDocumentService.upload(ORDER_ID, OrderDocumentType.INVOICE, file, ADMIN_USER_ID))
                    .isInstanceOf(InvalidDocumentException.class);

            verify(orderDocumentStorage, never()).upload(any(), anyString());
            verify(orderDocumentRepository, never()).save(any());
            verify(kafkaProducer, never()).sendOrderDocumentAdded(any(), anyString(), anyString());
        }

        @Test
        @DisplayName("отклоняет файл больше 20 МБ")
        void shouldRejectFileOverSizeLimit() {
            byte[] oversized = new byte[21 * 1024 * 1024];
            MockMultipartFile file = new MockMultipartFile(
                    "file", "invoice.pdf", "application/pdf", oversized);

            assertThatThrownBy(() -> orderDocumentService.upload(ORDER_ID, OrderDocumentType.INVOICE, file, ADMIN_USER_ID))
                    .isInstanceOf(InvalidDocumentException.class);

            verify(orderDocumentStorage, never()).upload(any(), anyString());
            verify(orderDocumentRepository, never()).save(any());
        }

        @Test
        @DisplayName("успешная загрузка сохраняет запись, загружает в storage и отправляет Kafka-событие")
        void shouldUploadAndPersistAndPublishEvent() {
            Order order = buildOrder();
            when(orderService.getOrder(ORDER_ID)).thenReturn(order);
            when(orderDocumentRepository.save(any(OrderDocument.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            MockMultipartFile file = new MockMultipartFile(
                    "file", "Счёт №1.pdf", "application/pdf", "content".getBytes());

            OrderDocument result = orderDocumentService.upload(ORDER_ID, OrderDocumentType.INVOICE, file, ADMIN_USER_ID);

            assertThat(result.getType()).isEqualTo(OrderDocumentType.INVOICE);
            assertThat(result.getOrderId()).isEqualTo(ORDER_ID);
            assertThat(result.getUploadedByUserId()).isEqualTo(ADMIN_USER_ID);
            assertThat(result.getOriginalFileName()).doesNotContain("/", "\\");

            verify(orderDocumentStorage, times(1)).upload(eq(file), anyString());
            verify(orderDocumentRepository, times(1)).save(any(OrderDocument.class));
            verify(kafkaProducer, times(1))
                    .sendOrderDocumentAdded(eq(order), eq(OrderDocumentType.INVOICE.getDisplayName()), anyString());
        }
    }

    @Nested
    @DisplayName("Доступ к чужому заказу")
    class OwnershipTests {

        @Test
        @DisplayName("listForUser с чужим заказом приводит к 404 (OrderDocumentNotFoundException), не к 400")
        void shouldDenyListForUserOnForeignOrder() {
            when(orderService.getOrderByIdAndUser(ORDER_ID, OTHER_USER_ID))
                    .thenThrow(new InvalidOrderStateException("Нет доступа к заказу: " + ORDER_ID));

            assertThatThrownBy(() -> orderDocumentService.listForUser(ORDER_ID, OTHER_USER_ID))
                    .isInstanceOf(OrderDocumentNotFoundException.class);

            verify(orderDocumentRepository, never()).findByOrderIdOrderByUploadedAtDesc(any());
        }

        @Test
        @DisplayName("downloadForUser с чужим заказом приводит к 404 (OrderDocumentNotFoundException), не к 400")
        void shouldDenyDownloadForUserOnForeignOrder() {
            when(orderService.getOrderByIdAndUser(ORDER_ID, OTHER_USER_ID))
                    .thenThrow(new InvalidOrderStateException("Нет доступа к заказу: " + ORDER_ID));

            UUID docId = UUID.randomUUID();

            assertThatThrownBy(() -> orderDocumentService.downloadForUser(ORDER_ID, docId, OTHER_USER_ID))
                    .isInstanceOf(OrderDocumentNotFoundException.class);

            verify(orderDocumentRepository, never()).findByIdAndOrderId(any(), any());
            verify(orderDocumentStorage, never()).downloadStream(anyString());
        }

        @Test
        @DisplayName("listForUser для владельца возвращает документы заказа")
        void shouldReturnDocumentsForOwner() {
            Order order = buildOrder();
            when(orderService.getOrderByIdAndUser(ORDER_ID, OWNER_USER_ID)).thenReturn(order);

            OrderDocument document = OrderDocument.builder()
                    .id(UUID.randomUUID())
                    .orderId(ORDER_ID)
                    .type(OrderDocumentType.UPD)
                    .originalFileName("upd.pdf")
                    .fileKey("orders/" + ORDER_ID + "/key_upd.pdf")
                    .sizeBytes(100L)
                    .build();
            when(orderDocumentRepository.findByOrderIdOrderByUploadedAtDesc(ORDER_ID))
                    .thenReturn(List.of(document));

            List<OrderDocument> result = orderDocumentService.listForUser(ORDER_ID, OWNER_USER_ID);

            assertThat(result).containsExactly(document);
        }
    }
}
