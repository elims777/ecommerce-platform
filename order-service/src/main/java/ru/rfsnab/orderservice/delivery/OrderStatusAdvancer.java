package ru.rfsnab.orderservice.delivery;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import ru.rfsnab.orderservice.models.entity.Order;
import ru.rfsnab.orderservice.models.entity.enums.DeliveryMethod;
import ru.rfsnab.orderservice.models.entity.enums.OrderStatus;
import ru.rfsnab.orderservice.service.OrderService;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Продвижение статуса заказа по его отправлениям (спек 6.8). Класс только решает, нужен ли переход;
 * сам переход выполняет {@link OrderService#updateStatus} — тот же путь, что ручная смена статуса менеджером
 * (валидация переходов, событие Kafka). Переход только вперёд; недопустимый пропускается без ошибки.
 * Вызывать внутри транзакции, в которой изменены отправления.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderStatusAdvancer {

    /** Цепочка статусов отгрузки по порядку; шаг назад по ней запрещён. */
    private static final List<OrderStatus> SHIPPING_CHAIN = List.of(
            OrderStatus.SHIPPED, OrderStatus.IN_TRANSIT, OrderStatus.ARRIVED_AT_TERMINAL, OrderStatus.DELIVERED);

    private static final Set<ShipmentStatusCode> MOVING = EnumSet.of(
            ShipmentStatusCode.IN_TRANSIT, ShipmentStatusCode.ARRIVED_AT_TERMINAL, ShipmentStatusCode.DELIVERED);

    private static final Set<ShipmentStatusCode> ARRIVED_OR_DELIVERED = EnumSet.of(
            ShipmentStatusCode.ARRIVED_AT_TERMINAL, ShipmentStatusCode.DELIVERED);

    private final ShipmentRepository shipmentRepository;
    private final OrderService orderService;

    /**
     * Пересчитывает статус заказа по его отправлениям и при необходимости переводит заказ вперёд.
     * Заказ до отгрузки переводится только в SHIPPED; дальше — по статусам отправлений.
     */
    public void advance(Order order) {
        List<Shipment> shipments = shipmentRepository.findAllByOrderIds(List.of(order.getId()));
        OrderStatus target = target(order, shipments);
        if (target == null) {
            return;
        }
        OrderStatus current = order.getStatus();
        int currentRank = SHIPPING_CHAIN.indexOf(current);
        if (currentRank < 0) {
            target = OrderStatus.SHIPPED;
        } else if (SHIPPING_CHAIN.indexOf(target) <= currentRank) {
            return;
        }
        if (!OrderService.isTransitionAllowed(current, target)) {
            log.debug("Заказ {}: переход {} → {} не допустим, пропущен", order.getOrderNumber(), current, target);
            return;
        }
        orderService.updateStatus(order.getId(), target);
        if (currentRank < 0) {
            // из PAID и т.п. сначала в SHIPPED; финальная накладная больше не опрашивается — досчитываем сразу
            order.setStatus(target);
            advance(order);
        }
    }

    /** Желаемый статус заказа по отправлениям; null, если отправлений нет. */
    private static OrderStatus target(Order order, List<Shipment> shipments) {
        if (shipments.isEmpty()) {
            return null;
        }
        boolean terminal = order.getDeliveryMethod() == DeliveryMethod.CARRIER_TERMINAL;
        if (order.getShipmentMode() != ShipmentMode.MULTIPLE && shipments.size() == 1) {
            return fromCode(shipments.get(0).getCarrierStatusCode(), terminal);
        }
        List<ShipmentStatusCode> codes = shipments.stream()
                .map(Shipment::getCarrierStatusCode)
                .filter(code -> code != ShipmentStatusCode.CANCELLED)
                .toList();
        boolean anyMoving = codes.stream().anyMatch(MOVING::contains);
        OrderStatus inProgress = anyMoving ? OrderStatus.IN_TRANSIT : OrderStatus.SHIPPED;
        if (!order.isShipmentsComplete() || codes.isEmpty()) {
            return inProgress;
        }
        if (codes.stream().allMatch(code -> code == ShipmentStatusCode.DELIVERED)) {
            return OrderStatus.DELIVERED;
        }
        if (codes.stream().allMatch(ARRIVED_OR_DELIVERED::contains)) {
            return terminal ? OrderStatus.ARRIVED_AT_TERMINAL : OrderStatus.IN_TRANSIT;
        }
        return inProgress;
    }

    private static OrderStatus fromCode(ShipmentStatusCode code, boolean terminal) {
        if (code == null) {
            return OrderStatus.SHIPPED;
        }
        return switch (code) {
            case IN_TRANSIT -> OrderStatus.IN_TRANSIT;
            case ARRIVED_AT_TERMINAL -> terminal ? OrderStatus.ARRIVED_AT_TERMINAL : OrderStatus.IN_TRANSIT;
            case DELIVERED -> OrderStatus.DELIVERED;
            case CANCELLED, PROBLEM, NOT_FOUND -> OrderStatus.SHIPPED;
        };
    }
}
