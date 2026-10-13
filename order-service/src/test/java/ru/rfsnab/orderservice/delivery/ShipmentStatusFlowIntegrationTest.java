package ru.rfsnab.orderservice.delivery;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import ru.rfsnab.orderservice.BaseIntegrationTest;
import ru.rfsnab.orderservice.kafka.OrderKafkaProducer;
import ru.rfsnab.orderservice.models.entity.Order;
import ru.rfsnab.orderservice.models.entity.enums.CustomerType;
import ru.rfsnab.orderservice.models.entity.enums.DeliveryMethod;
import ru.rfsnab.orderservice.models.entity.enums.OrderStatus;
import ru.rfsnab.orderservice.models.entity.enums.PaymentMethod;
import ru.rfsnab.orderservice.repository.OrderRepository;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

/**
 * Сквозной сценарий с настоящими ShipmentService, OrderStatusAdvancer и OrderService на реальной БД:
 * внесение накладной двигает оплаченный заказ в SHIPPED, статус ТК «доставлено» — в DELIVERED.
 * Только Kafka-продюсер замокан: проверяем, что на каждый переход ушёл ORDER_STATUS_CHANGED.
 */
@DisplayName("Продвижение статуса заказа по накладной (сквозное)")
class ShipmentStatusFlowIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private ShipmentService shipmentService;
    @Autowired
    private ShipmentRepository shipmentRepository;
    @Autowired
    private OrderRepository orderRepository;

    @MockitoBean
    private OrderKafkaProducer kafkaProducer;

    @AfterEach
    void cleanUp() {
        shipmentRepository.deleteAll();
        orderRepository.deleteAll();
    }

    @Test
    @DisplayName("PAID + накладная -> SHIPPED; статус ТК DELIVERED -> DELIVERED; на оба перехода ушёл ORDER_STATUS_CHANGED")
    void waybillThenDelivered() {
        List<OrderStatus> sent = new ArrayList<>();
        doAnswer(inv -> sent.add(((Order) inv.getArgument(0)).getStatus())).when(kafkaProducer).sendOrderStatusChanged(any());
        Order order = orderRepository.save(Order.builder().userId(1L).orderNumber("FLOW-1").status(OrderStatus.PAID)
                .paymentMethod(PaymentMethod.INVOICE).deliveryMethod(DeliveryMethod.CARRIER_ADDRESS)
                .totalAmount(BigDecimal.TEN).customerEmail("a@b.ru").customerType(CustomerType.B2B).build());

        ShipmentDto added = shipmentService.add(order.getId(), new AddShipmentRequest(CarrierCode.PEC, "FLOW-100", ShipmentMode.SINGLE), 7L);

        assertThat(orderRepository.findById(order.getId()).orElseThrow().getStatus()).isEqualTo(OrderStatus.SHIPPED);

        shipmentService.applyPolled(added.id(),
                Optional.of(new CarrierShipmentStatus(ShipmentStatusCode.DELIVERED, "Доставлен", null, null)), true);

        assertThat(orderRepository.findById(order.getId()).orElseThrow().getStatus()).isEqualTo(OrderStatus.DELIVERED);
        assertThat(sent).containsExactly(OrderStatus.SHIPPED, OrderStatus.DELIVERED);
    }
}
