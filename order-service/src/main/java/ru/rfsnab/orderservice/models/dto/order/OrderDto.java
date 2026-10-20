package ru.rfsnab.orderservice.models.dto.order;

import lombok.Builder;
import ru.rfsnab.orderservice.delivery.models.dto.ShipmentDto;
import ru.rfsnab.orderservice.delivery.models.entity.enums.CarrierCode;
import ru.rfsnab.orderservice.delivery.models.entity.enums.ShipmentMode;
import ru.rfsnab.orderservice.models.entity.enums.CustomerType;
import ru.rfsnab.orderservice.models.entity.enums.DeliveryMethod;
import ru.rfsnab.orderservice.models.entity.enums.OrderStatus;
import ru.rfsnab.orderservice.models.entity.enums.PaymentMethod;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Builder
public record OrderDto(
        UUID id,
        Long userId,
        String orderNumber,
        String externalId,
        OrderStatus status,
        PaymentMethod paymentMethod,
        DeliveryMethod deliveryMethod,
        BigDecimal totalAmount,
        List<OrderItemDto> items,
        AddressDto deliveryAddress,
        WarehousePointDto warehousePoint,
        CarrierCode carrierCode,
        String carrierName,
        Long carrierTerminalId,
        String terminalName,
        String terminalAddress,
        boolean terminalClosed,
        String terminalCurrentAddress,
        ShipmentMode shipmentMode,
        boolean shipmentsComplete,
        List<ShipmentDto> shipments,
        String trackingNumber,
        String customerEmail,
        String customerName,
        String customerPhone,
        String pickupRecipientName,
        String pickupRecipientPhone,
        String comment,
        CustomerType customerType,
        String companyName,
        String inn,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
