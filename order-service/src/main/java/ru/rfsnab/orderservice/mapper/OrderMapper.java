package ru.rfsnab.orderservice.mapper;

import ru.rfsnab.orderservice.models.dto.order.CreateOrderRequest;
import ru.rfsnab.orderservice.models.dto.order.OrderDto;
import ru.rfsnab.orderservice.models.dto.order.OrderSummaryDto;
import ru.rfsnab.orderservice.models.entity.Order;
import ru.rfsnab.orderservice.models.entity.WarehousePoint;
import ru.rfsnab.orderservice.models.entity.enums.DeliveryMethod;
import ru.rfsnab.orderservice.models.entity.enums.OrderStatus;

public class OrderMapper {

    /**
     * Order → OrderDto (полный, с данными о точке самовывоза).
     *
     * @param order заказ
     * @param warehousePoint точка самовывоза (nullable — null для DELIVERY заказов)
     */
    public static OrderDto toDto(Order order, WarehousePoint warehousePoint) {
        return new OrderDto(
                order.getId(),
                order.getUserId(),
                order.getOrderNumber(),
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
                order.getTrackingNumber(),
                order.getComment(),
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
                order.getStatus(),
                order.getItems().size(),
                order.getTotalAmount(),
                order.getCreatedAt()
        );
    }

    /**
     * CreateOrderRequest → Order entity.
     * Маппит только базовые поля из запроса.
     * Items, totalAmount, orderNumber заполняются в сервисном слое.
     *
     * @param userId идентификатор пользователя из JWT
     * @param request данные для создания заказа
     */
    public static Order toEntity(Long userId, CreateOrderRequest request) {
        Order order = Order.builder()
                .userId(userId)
                .status(OrderStatus.CREATED)
                .paymentMethod(request.paymentMethod())
                .deliveryMethod(request.deliveryMethod())
                .comment(request.comment())
                .build();

        if (request.deliveryMethod() == DeliveryMethod.PICKUP) {
            order.setWarehousePointId(request.warehousePointId());
        } else {
            order.setDeliveryAddress(
                    AddressMapper.mapToDeliveryAddress(request.deliveryAddress()));
        }

        return order;
    }
}