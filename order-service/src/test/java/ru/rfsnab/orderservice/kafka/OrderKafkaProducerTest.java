package ru.rfsnab.orderservice.kafka;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import ru.rfsnab.orderservice.delivery.mapper.ShipmentMapper;
import ru.rfsnab.orderservice.delivery.models.entity.Carrier;
import ru.rfsnab.orderservice.delivery.models.entity.Shipment;
import ru.rfsnab.orderservice.delivery.models.entity.enums.CarrierCode;
import ru.rfsnab.orderservice.delivery.repository.CarrierRepository;
import ru.rfsnab.orderservice.delivery.repository.ShipmentRepository;
import ru.rfsnab.orderservice.models.dto.event.OrderEvent;
import ru.rfsnab.orderservice.models.entity.Order;
import ru.rfsnab.orderservice.models.entity.enums.OrderStatus;
import ru.rfsnab.orderservice.repository.WarehousePointRepository;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrderKafkaProducer — отправления в событии смены статуса")
class OrderKafkaProducerTest {

    @Mock
    private KafkaTopicsProperties topics;
    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;
    @Mock
    private WarehousePointRepository warehousePointRepository;
    @Mock
    private ShipmentRepository shipmentRepository;
    @Mock
    private CarrierRepository carrierRepository;

    private OrderKafkaProducer producer;
    private Order order;

    @BeforeEach
    void setUp() {
        producer = new OrderKafkaProducer(topics, kafkaTemplate, warehousePointRepository, shipmentRepository,
                carrierRepository, Mappers.getMapper(ShipmentMapper.class));
        when(topics.getOrderEvents()).thenReturn("order-events");
        order = Order.builder().id(UUID.randomUUID()).orderNumber("ORD-1").userId(1L).customerEmail("a@rfsnab.ru")
                .status(OrderStatus.SHIPPED).totalAmount(BigDecimal.TEN).items(new ArrayList<>()).build();
    }

    private static Shipment shipment(CarrierCode carrier, String waybill) {
        return Shipment.builder().carrierCode(carrier).waybillNumber(waybill).carrierStatus("В пути").build();
    }

    private List<OrderEvent.ShipmentLine> sentShipments() {
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(kafkaTemplate).send(eq("order-events"), eq(order.getId().toString()), captor.capture());
        return ((OrderEvent) captor.getValue()).shipments();
    }

    @Test
    @DisplayName("ссылка «Отследить» подставляет закодированный номер в шаблон ТК; без шаблона и без ТК ссылки нет")
    void trackingUrlFromCarrierTemplate() {
        when(shipmentRepository.findAllByOrderIds(any())).thenReturn(List.of(
                shipment(CarrierCode.DELLIN, "a/b"), shipment(CarrierCode.PEC, "2"), shipment(CarrierCode.CDEK, "3")));
        when(carrierRepository.findAll()).thenReturn(List.of(
                Carrier.builder().code(CarrierCode.DELLIN).name("Деловые Линии").trackingUrlTemplate("https://t.example/{number}").build(),
                Carrier.builder().code(CarrierCode.PEC).name("ПЭК").build()));

        producer.sendOrderStatusChanged(order);

        assertThat(sentShipments()).containsExactly(
                new OrderEvent.ShipmentLine("Деловые Линии", "a/b", "https://t.example/a%2Fb", "В пути"),
                new OrderEvent.ShipmentLine("ПЭК", "2", null, "В пути"),
                new OrderEvent.ShipmentLine("CDEK", "3", null, "В пути"));
    }
}
