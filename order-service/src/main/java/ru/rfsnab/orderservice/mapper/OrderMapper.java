package ru.rfsnab.orderservice.mapper;

import ru.rfsnab.orderservice.delivery.models.dto.ShipmentDto;
import ru.rfsnab.orderservice.delivery.models.entity.CarrierTerminal;
import ru.rfsnab.orderservice.models.dto.order.CreateOrderRequest;
import ru.rfsnab.orderservice.models.dto.order.OrderDto;
import ru.rfsnab.orderservice.models.dto.order.OrderSummaryDto;
import ru.rfsnab.orderservice.models.entity.Order;
import ru.rfsnab.orderservice.models.entity.WarehousePoint;
import ru.rfsnab.orderservice.models.entity.enums.DeliveryMethod;
import ru.rfsnab.orderservice.models.entity.enums.OrderStatus;
import ru.rfsnab.orderservice.service.PriceSelector;

import java.util.List;
import java.util.Objects;

public class OrderMapper {
    private OrderMapper() {
    }

    /**
     * Order → OrderDto (полный, с данными о точке самовывоза).
     *
     * @param order заказ
     * @param warehousePoint точка самовывоза (nullable — null для DELIVERY заказов)
     */
    public static OrderDto toDto(Order order, WarehousePoint warehousePoint) {
        return toDto(order, warehousePoint, null);
    }

    /**
     * Order → OrderDto с точкой самовывоза и текущим состоянием терминала ТК.
     *
     * @param terminal терминал заказа (nullable — null, если способ доставки без терминала)
     */
    public static OrderDto toDto(Order order, WarehousePoint warehousePoint, CarrierTerminal terminal) {
        return toDto(order, warehousePoint, terminal, List.of());
    }

    /**
     * Order → OrderDto с точкой самовывоза, терминалом ТК и отправлениями заказа.
     *
     * @param shipments отправления заказа (не null; пустой список, если их нет)
     */
    public static OrderDto toDto(Order order, WarehousePoint warehousePoint, CarrierTerminal terminal,
                                 List<ShipmentDto> shipments) {
        return new OrderDto(
                order.getId(),
                order.getUserId(),
                order.getOrderNumber(),
                order.getExternalId(),
                order.getStatus(),
                order.getPaymentMethod(),
                order.getDeliveryMethod(),
                order.getTotalAmount(),
                OrderItemMapper.toDtoList(order.getItems()),
                order.getDeliveryAddress() != null
                        ? AddressMapper.mapToAddressDto(order.getDeliveryAddress())
                        : null,
                warehousePoint != null
                        ? WarehousePointMapper.mapToWarehousePointDto(warehousePoint)
                        : null,
                order.getCarrierCode(),
                order.getCarrierName(),
                order.getCarrierTerminalId(),
                order.getTerminalName(),
                order.getTerminalAddress(),
                order.getShippingWarehouseId(),
                order.getShippingWarehouseName(),
                order.getShippingWarehouseCity(),
                terminal != null && !terminal.isActive(),
                terminal != null && !Objects.equals(terminal.getAddress(), order.getTerminalAddress())
                        ? terminal.getAddress()
                        : null,
                order.getShipmentMode(),
                order.isShipmentsComplete(),
                shipments,
                order.getTrackingNumber(),
                order.getCustomerEmail(),
                order.getCustomerName(),
                order.getCustomerPhone(),
                order.getPickupRecipientName(),
                order.getPickupRecipientPhone(),
                order.getComment(),
                order.getCustomerType(),
                order.getCompanyName(),
                order.getInn(),
                order.getCreatedAt(),
                order.getUpdatedAt()
        );
    }

    /**
     * Order → OrderDto (без загрузки WarehousePoint).
     */
    public static OrderDto toDto(Order order) {
        return toDto(order, null);
    }

    /**
     * Order → OrderSummaryDto (для списков с пагинацией).
     */
    public static OrderSummaryDto toSummaryDto(Order order) {
        return new OrderSummaryDto(
                order.getId(),
                order.getOrderNumber(),
                order.getExternalId(),
                order.getStatus(),
                order.getCustomerType(),
                order.getItems().size(),
                order.getTotalAmount(),
                order.getCustomerEmail(),
                order.getCustomerName(),
                order.getCustomerPhone(),
                order.getCompanyName(),
                order.getCreatedAt()
        );
    }

    /**
     * CreateOrderRequest → Order entity.
     * Маппит только базовые поля из запроса.
     * Items, totalAmount, orderNumber заполняются в сервисном слое.
     *
     * @param userId идентификатор пользователя из JWT
     * @param customerEmail email пользователя из JWT
     * @param request данные для создания заказа
     */
    public static Order toEntity(Long userId, String customerEmail, String clientType, CreateOrderRequest request) {
        Order order = Order.builder()
                .userId(userId)
                .status(OrderStatus.CREATED)
                .paymentMethod(request.paymentMethod())
                .deliveryMethod(request.deliveryMethod())
                .customerEmail(customerEmail)
                .comment(request.comment())
                .customerType(PriceSelector.parseCustomerType(clientType))
                .build();

        if (request.deliveryMethod() == DeliveryMethod.PICKUP) {
            order.setWarehousePointId(request.warehousePointId());
            order.setPickupRecipientName(request.pickupRecipientName());
            order.setPickupRecipientPhone(request.pickupRecipientPhone());
        } else if (request.deliveryMethod() == DeliveryMethod.CARRIER_TERMINAL) {
            // адрес не храним: место получения — снимок терминала
            order.setCarrierCode(request.carrierCode());
            order.setPickupRecipientName(request.pickupRecipientName());
            order.setPickupRecipientPhone(request.pickupRecipientPhone());
        } else {
            order.setDeliveryAddress(
                    AddressMapper.mapToDeliveryAddress(request.deliveryAddress()));
            if (request.deliveryMethod() == DeliveryMethod.CARRIER_ADDRESS) {
                order.setCarrierCode(request.carrierCode());
            }
        }

        return order;
    }

}