package ru.rfsnab.orderservice.kafka;

import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import ru.rfsnab.orderservice.delivery.mapper.ShipmentMapper;
import ru.rfsnab.orderservice.delivery.models.dto.event.CarrierSyncEvent;
import ru.rfsnab.orderservice.delivery.models.dto.event.ShipmentNotFoundEvent;
import ru.rfsnab.orderservice.delivery.models.entity.Carrier;
import ru.rfsnab.orderservice.delivery.models.entity.Shipment;
import ru.rfsnab.orderservice.delivery.models.entity.enums.CarrierCode;
import ru.rfsnab.orderservice.delivery.repository.CarrierRepository;
import ru.rfsnab.orderservice.delivery.repository.ShipmentRepository;
import ru.rfsnab.orderservice.models.dto.event.OrderEvent;
import ru.rfsnab.orderservice.models.entity.Order;
import ru.rfsnab.orderservice.models.entity.WarehousePoint;
import ru.rfsnab.orderservice.repository.WarehousePointRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Отправка Kafka events для заказов.
 */
@Component
@RequiredArgsConstructor
public class OrderKafkaProducer {
    private static final String CARRIER_SYNC_KEY = "carrier-sync";
    private static final String SHIPMENT_NOT_FOUND_KEY = "shipment-not-found:";

    private final KafkaTopicsProperties topics;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final WarehousePointRepository warehousePointRepository;
    private final ShipmentRepository shipmentRepository;
    private final CarrierRepository carrierRepository;
    private final ShipmentMapper shipmentMapper;

    private void send(String eventType, Order order){
        send(eventType, order, null, null);
    }

    private void send(String eventType, Order order, String documentType, String documentFileName){
        OrderEvent event = OrderEvent.builder()
                .eventType(eventType)
                .orderId(order.getId())
                .orderNumber(order.getOrderNumber())
                .userId(order.getUserId())
                .status(order.getStatus().getDisplayName())
                .totalAmount(order.getTotalAmount())
                .customerEmail(order.getCustomerEmail())
                .customerName(order.getCustomerName())
                .customerPhone(order.getCustomerPhone())
                .timestamp(LocalDateTime.now())
                .items(order.getItems().stream()
                        .map(i -> new OrderEvent.OrderItemLine(i.getProductName(), i.getQuantity(), i.getPrice(), i.getVariantAttributes(), i.getSku()))
                        .toList())
                .deliveryMethod(order.getDeliveryMethod() != null ? order.getDeliveryMethod().name() : null)
                .paymentMethod(order.getPaymentMethod() != null ? order.getPaymentMethod().name() : null)
                .comment(order.getComment())
                .deliveryAddress(order.getDeliveryAddress() != null
                        ? new OrderEvent.DeliveryAddressDto(
                                order.getDeliveryAddress().getCity(),
                                order.getDeliveryAddress().getStreet(),
                                order.getDeliveryAddress().getBuilding(),
                                order.getDeliveryAddress().getApartment(),
                                order.getDeliveryAddress().getPostalCode(),
                                order.getDeliveryAddress().getPhone(),
                                order.getDeliveryAddress().getRecipientName())
                        : null)
                .customerType(order.getCustomerType() != null ? order.getCustomerType().name() : null)
                .companyName(order.getCompanyName())
                .inn(order.getInn())
                .pickupPoint(buildPickupPoint(order))
                .documentType(documentType)
                .documentFileName(documentFileName)
                .carrierName(order.getCarrierName())
                .terminalName(order.getTerminalName())
                .terminalAddress(order.getTerminalAddress())
                .shipments("ORDER_STATUS_CHANGED".equals(eventType) ? buildShipments(order) : null)
                .build();

        sendAfterCommit(order.getId().toString(), event);
    }

    /** Событие уходит только после коммита транзакции (при rollback не отправляется); вне транзакции — сразу. */
    private void sendAfterCommit(String key, Object payload) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            kafkaTemplate.send(topics.getOrderEvents(), key, payload);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                kafkaTemplate.send(topics.getOrderEvents(), key, payload);
            }
        });
    }

    private OrderEvent.PickupPointDto buildPickupPoint(Order order) {
        if (order.getWarehousePointId() == null) {
            return null;
        }
        WarehousePoint point = warehousePointRepository.findById(order.getWarehousePointId()).orElse(null);
        if (point == null) {
            return null;
        }
        return new OrderEvent.PickupPointDto(point.getName(), point.getCity(), point.getStreet(),
                point.getBuilding(), point.getPostalCode(), point.getPhoneNumber(), point.getWorkingHours());
    }

    /** Отправления заказа для письма о смене статуса: ТК, номер, ссылка «Отследить», статус ТК. */
    private List<OrderEvent.ShipmentLine> buildShipments(Order order) {
        List<Shipment> shipments = shipmentRepository.findAllByOrderIds(List.of(order.getId()));
        if (shipments.isEmpty()) {
            return List.of();
        }
        Map<CarrierCode, Carrier> carriers = carrierRepository.findAll().stream()
                .collect(Collectors.toMap(Carrier::getCode, Function.identity()));
        return shipments.stream().map(s -> {
            Carrier carrier = carriers.get(s.getCarrierCode());
            return new OrderEvent.ShipmentLine(carrier == null ? s.getCarrierCode().name() : carrier.getName(),
                    s.getWaybillNumber(), carrier == null ? null : shipmentMapper.trackingUrl(carrier, s.getWaybillNumber()),
                    s.getCarrierStatus());
        }).toList();
    }

    /**
     * Накладная не найдена в ТК 24 часа → письмо менеджеру (топик order-events, событие SHIPMENT_NOT_FOUND).
     */
    public void sendShipmentNotFound(ShipmentNotFoundEvent event) {
        sendAfterCommit(SHIPMENT_NOT_FOUND_KEY + event.waybillNumber(), event);
    }

    /**
     * Отчёт синхронизации терминалов ТК → письмо менеджеру (топик order-events, событие CARRIER_SYNC_COMPLETED).
     */
    public void sendCarrierSyncCompleted(CarrierSyncEvent event) {
        sendAfterCommit(CARRIER_SYNC_KEY, event);
    }

    public void sendOrderCreated(Order order) {
        send("ORDER_CREATED", order);
    }

    public void sendOrderPaid(Order order) {
        send("ORDER_PAID", order);
    }

    public void sendOrderCancelled(Order order) {
        send("ORDER_CANCELLED", order);
    }

    public void sendOrderStatusChanged(Order order) {
        send("ORDER_STATUS_CHANGED", order);
    }

    public void sendOrderDocumentAdded(Order order, String documentTypeName, String fileName) {
        send("ORDER_DOCUMENT_ADDED", order, documentTypeName, fileName);
    }
}
