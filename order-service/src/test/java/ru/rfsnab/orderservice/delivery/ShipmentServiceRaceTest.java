package ru.rfsnab.orderservice.delivery;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;
import ru.rfsnab.orderservice.kafka.OrderKafkaProducer;
import ru.rfsnab.orderservice.models.entity.Order;
import ru.rfsnab.orderservice.repository.OrderRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Гонка при внесении одной и той же накладной: проверка existsBy прошла, а вставку опередил другой запрос. */
@DisplayName("ShipmentService: гонка по uq_shipments_carrier_waybill")
class ShipmentServiceRaceTest {

    @Test
    @DisplayName("DataIntegrityViolationException при вставке → ShipmentAlreadyExistsException (409)")
    void uniqueViolationBecomesConflict() {
        ShipmentRepository shipments = mock(ShipmentRepository.class);
        OrderRepository orders = mock(OrderRepository.class);
        TransactionTemplate tx = mock(TransactionTemplate.class);
        when(tx.execute(any())).thenAnswer(inv -> ((TransactionCallback<?>) inv.getArgument(0)).doInTransaction(null));
        UUID orderId = UUID.randomUUID();
        when(orders.findWithItemsById(orderId)).thenReturn(Optional.of(new Order()));
        when(shipments.existsByCarrierCodeAndWaybillNumber(CarrierCode.PEC, "1")).thenReturn(false);
        when(shipments.countByOrderId(orderId)).thenReturn(0L);
        when(shipments.saveAndFlush(any(Shipment.class))).thenThrow(new DataIntegrityViolationException("uq_shipments_carrier_waybill"));
        ShipmentService service = new ShipmentService(shipments, mock(CarrierRepository.class), orders, List.of(),
                mock(OrderStatusAdvancer.class), mock(OrderKafkaProducer.class), tx);

        assertThatThrownBy(() -> service.add(orderId, new AddShipmentRequest(CarrierCode.PEC, "1", ShipmentMode.SINGLE), 7L))
                .isInstanceOf(ShipmentAlreadyExistsException.class);
    }
}
