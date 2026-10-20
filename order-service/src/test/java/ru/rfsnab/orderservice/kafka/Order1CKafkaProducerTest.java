package ru.rfsnab.orderservice.kafka;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import ru.rfsnab.orderservice.delivery.models.entity.enums.CarrierCode;
import ru.rfsnab.orderservice.models.dto.event.Order1CExportEvent;
import ru.rfsnab.orderservice.models.entity.DeliveryAddress;
import ru.rfsnab.orderservice.models.entity.Order;
import ru.rfsnab.orderservice.models.entity.WarehousePoint;
import ru.rfsnab.orderservice.models.entity.enums.DeliveryMethod;
import ru.rfsnab.orderservice.models.entity.enums.PaymentMethod;
import ru.rfsnab.orderservice.repository.WarehousePointRepository;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Order1CKafkaProducer — комментарий для 1С")
class Order1CKafkaProducerTest {

    @Mock
    private KafkaTopicsProperties topics;
    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;
    @Mock
    private WarehousePointRepository warehousePointRepository;

    @InjectMocks
    private Order1CKafkaProducer producer;

    @BeforeEach
    void setUp() {
        when(topics.getOrder1cExport()).thenReturn("order-1c-export");
        when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(new CompletableFuture<>());
    }

    private Order carrierOrder(String comment) {
        return Order.builder()
                .id(UUID.randomUUID()).orderNumber("ORD-1").userId(1L).customerEmail("a@rfsnab.ru")
                .paymentMethod(PaymentMethod.INVOICE)
                .deliveryMethod(DeliveryMethod.CARRIER_ADDRESS).carrierCode(CarrierCode.DELLIN).carrierName("Деловые Линии")
                .deliveryAddress(DeliveryAddress.builder().postalCode("630000").city("Новосибирск")
                        .street("Ленина").building("1").recipientName("Иванов").phone("+79990000000").build())
                .comment(comment).totalAmount(BigDecimal.TEN)
                .build();
    }

    private Order1CExportEvent sentEvent(Order order) {
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(kafkaTemplate).send(eq("order-1c-export"), eq(order.getId().toString()), captor.capture());
        return (Order1CExportEvent) captor.getValue();
    }

    private String sentComment(Order order) {
        return sentEvent(order).comment();
    }

    @Test
    @DisplayName("блок доставки с ТК первым, затем --- и комментарий клиента")
    void deliveryBlockFirstThenClientComment() {
        Order order = carrierOrder("Позвонить заранее");

        producer.sendOrderFor1C(order);

        assertThat(sentComment(order)).isEqualTo("""
                Способ доставки: Доставка до адреса — Деловые Линии
                Адрес доставки: 630000, Новосибирск, Ленина, д. 1
                Получатель: Иванов, +79990000000
                ---
                Позвонить заранее""");
    }

    @Test
    @DisplayName("без комментария клиента — нет разделителя")
    void noSeparatorWithoutClientComment() {
        Order order = carrierOrder(null);

        producer.sendOrderFor1C(order);

        assertThat(sentComment(order)).doesNotContain("---")
                .startsWith("Способ доставки: Доставка до адреса — Деловые Линии");
    }

    @Test
    @DisplayName("пустой индекс не даёт лишней запятой в адресе")
    void blankPostalCodeSkipped() {
        Order order = carrierOrder(null);
        order.getDeliveryAddress().setPostalCode("");

        producer.sendOrderFor1C(order);

        assertThat(sentComment(order)).contains("Адрес доставки: Новосибирск, Ленина, д. 1");
    }

    @Test
    @DisplayName("самовывоз: пустой индекс склада не даёт лишней запятой")
    void pickupBlankPostalCodeSkipped() {
        Order order = Order.builder()
                .id(UUID.randomUUID()).orderNumber("ORD-2").userId(1L).customerEmail("a@rfsnab.ru")
                .paymentMethod(PaymentMethod.INVOICE)
                .deliveryMethod(DeliveryMethod.PICKUP).warehousePointId(5L)
                .totalAmount(BigDecimal.TEN)
                .build();
        when(warehousePointRepository.findById(5L)).thenReturn(Optional.of(WarehousePoint.builder()
                .name("Склад").postalCode("").city("Новосибирск").street("Ленина").building("1").build()));

        producer.sendOrderFor1C(order);

        assertThat(sentComment(order)).contains("Пункт самовывоза: Склад, Новосибирск, Ленина, д. 1");
    }

    @Test
    @DisplayName("терминал ТК без адреса: строка терминала из снимка, получатель — pickupRecipientName/pickupRecipientPhone, а не заказчик")
    void terminalLineWithoutAddress() {
        Order order = carrierOrder("Позвонить заранее");
        order.setDeliveryAddress(null);
        order.setCustomerName("Петров Пётр");
        order.setCustomerPhone("+79991112233");
        order.setPickupRecipientName("Сидоров Сидор");
        order.setPickupRecipientPhone("+79995554433");
        order.setDeliveryMethod(DeliveryMethod.CARRIER_TERMINAL);
        order.setTerminalName("Терминал Толмачёво");
        order.setTerminalAddress("Новосибирск, ул. Ленина, 1");

        producer.sendOrderFor1C(order);

        assertThat(sentComment(order)).isEqualTo("""
                Способ доставки: До терминала ТК — Деловые Линии
                Терминал: Терминал Толмачёво, Новосибирск, ул. Ленина, 1
                Получатель: Сидоров Сидор, +79995554433
                ---
                Позвонить заранее""");
        Order1CExportEvent event = sentEvent(order);
        assertThat(event.recipientName()).isEqualTo("Сидоров Сидор");
        assertThat(event.recipientPhone()).isEqualTo("+79995554433");
        assertThat(event.city()).isNull();
    }

    @Test
    @DisplayName("в транзакции событие уходит только в afterCommit; без транзакции — сразу")
    void sendsOnlyAfterCommit() {
        Order inTx = carrierOrder(null);
        TransactionSynchronizationManager.initSynchronization();
        try {
            producer.sendOrderFor1C(inTx);
            verify(kafkaTemplate, never()).send(anyString(), anyString(), any());

            TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
            verify(kafkaTemplate, times(1)).send(anyString(), anyString(), any());
        } finally {
            TransactionSynchronizationManager.clear();
        }

        Order noTx = carrierOrder(null);
        producer.sendOrderFor1C(noTx);
        verify(kafkaTemplate, times(2)).send(anyString(), anyString(), any());
    }
}
