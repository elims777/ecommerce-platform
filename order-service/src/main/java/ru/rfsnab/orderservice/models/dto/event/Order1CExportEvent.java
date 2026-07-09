package ru.rfsnab.orderservice.models.dto.event;

import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
/**
 * Kafka event для выгрузки заказа в 1С.
 * Отдельный топик "order-1c-export" — полные данные заказа.
 * Отправляется при создании заказа (ORDER_CREATED).
 */
@Builder
public record Order1CExportEvent(
        UUID orderId,
        String orderNumber,
        LocalDateTime createdAt,

        // Покупатель
        Long userId,
        String customerEmail,
        String customerType,
        String companyName,
        String inn,
        String customerName,
        String recipientName,
        String recipientPhone,

        // Доставка
        String deliveryMethod,
        String city,
        String street,
        String building,
        String apartment,
        String postalCode,

        // Самовывоз
        Long warehousePointId,

        // Оплата
        String paymentMethod,
        BigDecimal totalAmount,
        String comment,

        // Товары
        List<ExportOrderItem> items
) {

    @Builder
    public record ExportOrderItem(
            Long productId,
            String externalId,
            String productName,
            Integer quantity,
            BigDecimal price
    ) {}
}
