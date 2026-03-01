package ru.rfsnab.orderservice.mapper;

import ru.rfsnab.orderservice.models.dto.order.OrderItemDto;
import ru.rfsnab.orderservice.models.entity.Order;
import ru.rfsnab.orderservice.models.entity.OrderItem;

import java.math.BigDecimal;
import java.util.List;

public class OrderItemMapper {

    /**
     * OrderItem → OrderItemDto.
     * Subtotal вычисляется как price × quantity.
     */
    public static OrderItemDto toDto(OrderItem item) {
        BigDecimal subtotal = item.getPrice()
                .multiply(BigDecimal.valueOf(item.getQuantity()));

        return new OrderItemDto(
                item.getProductId(),
                item.getProductName(),
                item.getQuantity(),
                item.getPrice(),
                subtotal
        );
    }

    /**
     * Маппинг списка OrderItem → OrderItemDto.
     */
    public static List<OrderItemDto> toDtoList(List<OrderItem> items) {
        if (items == null) {
            return List.of();
        }
        return items.stream()
                .map(OrderItemMapper::toDto)
                .toList();
    }

    /**
     * OrderItemDto → OrderItem entity.
     * productName и price берутся из DTO (snapshot на момент создания/обновления).
     * Связь с Order устанавливается через параметр.
     *
     * @param dto данные позиции
     * @param order родительский заказ для связи ManyToOne
     */
    public static OrderItem toEntity(OrderItemDto dto, Order order) {
        return OrderItem.builder()
                .order(order)
                .productId(dto.productId())
                .productName(dto.productName())
                .quantity(dto.quantity())
                .price(dto.price())
                .build();
    }

    /**
     * Маппинг списка OrderItemDto → OrderItem.
     */
    public static List<OrderItem> toEntityList(List<OrderItemDto> dtos, Order order) {
        if (dtos == null) {
            return List.of();
        }
        return dtos.stream()
                .map(dto -> toEntity(dto, order))
                .toList();
    }
}