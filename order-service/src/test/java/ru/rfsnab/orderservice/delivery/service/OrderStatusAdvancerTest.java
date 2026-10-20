package ru.rfsnab.orderservice.delivery.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import ru.rfsnab.orderservice.delivery.models.entity.Shipment;
import ru.rfsnab.orderservice.delivery.models.entity.enums.ShipmentMode;
import ru.rfsnab.orderservice.delivery.models.entity.enums.ShipmentStatusCode;
import ru.rfsnab.orderservice.delivery.repository.ShipmentRepository;
import ru.rfsnab.orderservice.models.entity.Order;
import ru.rfsnab.orderservice.models.entity.enums.DeliveryMethod;
import ru.rfsnab.orderservice.models.entity.enums.OrderStatus;
import ru.rfsnab.orderservice.service.OrderService;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("OrderStatusAdvancer")
class OrderStatusAdvancerTest {

    @Mock
    private ShipmentRepository shipmentRepository;
    @Mock
    private OrderService orderService;
    @InjectMocks
    private OrderStatusAdvancer advancer;

    private Order order(OrderStatus status, ShipmentMode mode, boolean complete, DeliveryMethod method) {
        return Order.builder().id(UUID.randomUUID()).orderNumber("RF-1").status(status).shipmentMode(mode)
                .shipmentsComplete(complete).deliveryMethod(method).build();
    }

    private void shipments(Order order, ShipmentStatusCode... codes) {
        List<Shipment> list = Arrays.stream(codes)
                .map(code -> Shipment.builder().carrierStatusCode(code).build()).toList();
        when(shipmentRepository.findAllByOrderIds(List.of(order.getId()))).thenReturn(list);
    }

    private void expectMovedTo(Order order, OrderStatus status) {
        verify(orderService).updateStatus(order.getId(), status);
    }

    private void expectNotMoved() {
        verify(orderService, never()).updateStatus(any(), any());
    }

    @Nested
    @DisplayName("SINGLE")
    class Single {

        @Test
        @DisplayName("первая накладная без статуса: оплаченный заказ → SHIPPED")
        void firstWaybillShips() {
            Order order = order(OrderStatus.PAID, ShipmentMode.SINGLE, false, DeliveryMethod.CARRIER_ADDRESS);
            shipments(order, (ShipmentStatusCode) null);

            advancer.advance(order);

            expectMovedTo(order, OrderStatus.SHIPPED);
        }

        @Test
        @DisplayName("до отгрузки заказ сначала идёт в SHIPPED, затем сразу к статусу ТК (IN_TRANSIT)")
        void beforeShippingShippedThenTarget() {
            Order order = order(OrderStatus.PAID, ShipmentMode.SINGLE, false, DeliveryMethod.CARRIER_ADDRESS);
            shipments(order, ShipmentStatusCode.IN_TRANSIT);

            advancer.advance(order);

            expectMovedTo(order, OrderStatus.SHIPPED);
            expectMovedTo(order, OrderStatus.IN_TRANSIT);
        }

        @Test
        @DisplayName("оплаченный заказ + накладная уже DELIVERED: не застревает в SHIPPED")
        void paidWithDeliveredWaybillReachesDelivered() {
            Order order = order(OrderStatus.PAID, ShipmentMode.SINGLE, false, DeliveryMethod.CARRIER_ADDRESS);
            shipments(order, ShipmentStatusCode.DELIVERED);

            advancer.advance(order);

            expectMovedTo(order, OrderStatus.SHIPPED);
            expectMovedTo(order, OrderStatus.DELIVERED);
        }

        @Test
        @DisplayName("накладная только оформлена в ТК (CREATED): оплаченный заказ → SHIPPED и дальше не идёт")
        void createdShipmentOnlyShips() {
            Order order = order(OrderStatus.PAID, ShipmentMode.SINGLE, false, DeliveryMethod.CARRIER_ADDRESS);
            shipments(order, ShipmentStatusCode.CREATED);

            advancer.advance(order);

            expectMovedTo(order, OrderStatus.SHIPPED);
            verify(orderService, never()).updateStatus(order.getId(), OrderStatus.IN_TRANSIT);
        }

        @Test
        @DisplayName("SHIPPED + IN_TRANSIT → IN_TRANSIT")
        void inTransit() {
            Order order = order(OrderStatus.SHIPPED, ShipmentMode.SINGLE, false, DeliveryMethod.CARRIER_ADDRESS);
            shipments(order, ShipmentStatusCode.IN_TRANSIT);

            advancer.advance(order);

            expectMovedTo(order, OrderStatus.IN_TRANSIT);
        }

        @Test
        @DisplayName("терминал: ARRIVED_AT_TERMINAL → ARRIVED_AT_TERMINAL")
        void terminalArrives() {
            Order order = order(OrderStatus.IN_TRANSIT, ShipmentMode.SINGLE, false, DeliveryMethod.CARRIER_TERMINAL);
            shipments(order, ShipmentStatusCode.ARRIVED_AT_TERMINAL);

            advancer.advance(order);

            expectMovedTo(order, OrderStatus.ARRIVED_AT_TERMINAL);
        }

        @Test
        @DisplayName("адрес: ARRIVED_AT_TERMINAL у ТК не меняет IN_TRANSIT заказа")
        void addressStaysInTransit() {
            Order order = order(OrderStatus.IN_TRANSIT, ShipmentMode.SINGLE, false, DeliveryMethod.CARRIER_ADDRESS);
            shipments(order, ShipmentStatusCode.ARRIVED_AT_TERMINAL);

            advancer.advance(order);

            expectNotMoved();
        }

        @Test
        @DisplayName("пропущенный IN_TRANSIT: SHIPPED → DELIVERED напрямую")
        void skippedInTransit() {
            Order order = order(OrderStatus.SHIPPED, ShipmentMode.SINGLE, false, DeliveryMethod.CARRIER_ADDRESS);
            shipments(order, ShipmentStatusCode.DELIVERED);

            advancer.advance(order);

            expectMovedTo(order, OrderStatus.DELIVERED);
        }

        @Test
        @DisplayName("только вперёд: IN_TRANSIT у отправления не возвращает заказ из ARRIVED_AT_TERMINAL")
        void neverBackwards() {
            Order order = order(OrderStatus.ARRIVED_AT_TERMINAL, ShipmentMode.SINGLE, false, DeliveryMethod.CARRIER_TERMINAL);
            shipments(order, ShipmentStatusCode.IN_TRANSIT);

            advancer.advance(order);

            expectNotMoved();
        }

        @Test
        @DisplayName("PROBLEM, NOT_FOUND и CANCELLED статус заказа не меняют")
        void problemCodesDoNotMove() {
            for (ShipmentStatusCode code : List.of(ShipmentStatusCode.PROBLEM, ShipmentStatusCode.NOT_FOUND, ShipmentStatusCode.CANCELLED)) {
                Order order = order(OrderStatus.IN_TRANSIT, ShipmentMode.SINGLE, false, DeliveryMethod.CARRIER_ADDRESS);
                shipments(order, code);

                advancer.advance(order);
            }

            expectNotMoved();
        }

        @Test
        @DisplayName("недопустимый переход (PROCESSING → SHIPPED) пропускается без ошибки")
        void invalidTransitionSkipped() {
            Order order = order(OrderStatus.PROCESSING, ShipmentMode.SINGLE, false, DeliveryMethod.CARRIER_ADDRESS);
            shipments(order, ShipmentStatusCode.IN_TRANSIT);

            advancer.advance(order);

            expectNotMoved();
        }

        @Test
        @DisplayName("завершённый и отменённый заказы не трогаются")
        void finishedOrdersUntouched() {
            for (OrderStatus status : List.of(OrderStatus.COMPLETED, OrderStatus.CANCELLED, OrderStatus.REFUNDED)) {
                Order order = order(status, ShipmentMode.SINGLE, false, DeliveryMethod.CARRIER_ADDRESS);
                shipments(order, ShipmentStatusCode.DELIVERED);

                advancer.advance(order);
            }

            expectNotMoved();
        }

        @Test
        @DisplayName("нет отправлений — ничего не делает")
        void noShipments() {
            Order order = order(OrderStatus.PAID, ShipmentMode.SINGLE, false, DeliveryMethod.CARRIER_ADDRESS);
            shipments(order);

            advancer.advance(order);

            expectNotMoved();
        }
    }

    @Nested
    @DisplayName("MULTIPLE")
    class Multiple {

        @Test
        @DisplayName("пока не нажато «все внесены»: все DELIVERED дают максимум IN_TRANSIT")
        void beforeCompleteCappedAtInTransit() {
            Order order = order(OrderStatus.SHIPPED, ShipmentMode.MULTIPLE, false, DeliveryMethod.CARRIER_TERMINAL);
            shipments(order, ShipmentStatusCode.DELIVERED, ShipmentStatusCode.ARRIVED_AT_TERMINAL);

            advancer.advance(order);

            expectMovedTo(order, OrderStatus.IN_TRANSIT);
        }

        @Test
        @DisplayName("после «все внесены»: все доставлены → DELIVERED")
        void afterCompleteAllDelivered() {
            Order order = order(OrderStatus.IN_TRANSIT, ShipmentMode.MULTIPLE, true, DeliveryMethod.CARRIER_ADDRESS);
            shipments(order, ShipmentStatusCode.DELIVERED, ShipmentStatusCode.DELIVERED);

            advancer.advance(order);

            expectMovedTo(order, OrderStatus.DELIVERED);
        }

        @Test
        @DisplayName("после «все внесены»: прибыли/доставлены (терминал) → ARRIVED_AT_TERMINAL")
        void afterCompleteAllArrived() {
            Order order = order(OrderStatus.IN_TRANSIT, ShipmentMode.MULTIPLE, true, DeliveryMethod.CARRIER_TERMINAL);
            shipments(order, ShipmentStatusCode.DELIVERED, ShipmentStatusCode.ARRIVED_AT_TERMINAL);

            advancer.advance(order);

            expectMovedTo(order, OrderStatus.ARRIVED_AT_TERMINAL);
        }

        @Test
        @DisplayName("после «все внесены»: одно отправление ещё в пути → остаётся IN_TRANSIT")
        void afterCompleteOneStillMoving() {
            Order order = order(OrderStatus.IN_TRANSIT, ShipmentMode.MULTIPLE, true, DeliveryMethod.CARRIER_TERMINAL);
            shipments(order, ShipmentStatusCode.DELIVERED, ShipmentStatusCode.IN_TRANSIT);

            advancer.advance(order);

            expectNotMoved();
        }

        @Test
        @DisplayName("две накладные без выставленного режима считаются как MULTIPLE: не DELIVERED, пока вторая в пути")
        void twoShipmentsWithoutModeTreatedAsMultiple() {
            Order order = order(OrderStatus.IN_TRANSIT, null, true, DeliveryMethod.CARRIER_ADDRESS);
            shipments(order, ShipmentStatusCode.DELIVERED, ShipmentStatusCode.IN_TRANSIT);

            advancer.advance(order);

            verify(orderService, never()).updateStatus(order.getId(), OrderStatus.DELIVERED);
        }

        @Test
        @DisplayName("отменённое отправление в расчёте «все» не участвует")
        void cancelledIgnored() {
            Order order = order(OrderStatus.IN_TRANSIT, ShipmentMode.MULTIPLE, true, DeliveryMethod.CARRIER_ADDRESS);
            shipments(order, ShipmentStatusCode.DELIVERED, ShipmentStatusCode.CANCELLED);

            advancer.advance(order);

            expectMovedTo(order, OrderStatus.DELIVERED);
        }
    }
}
