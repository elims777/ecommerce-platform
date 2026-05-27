package ru.rfsnab.orderservice.models.dto.payment;

import ru.rfsnab.orderservice.models.entity.Order;
import ru.rfsnab.orderservice.models.entity.enums.PaymentMethod;

import java.math.BigDecimal;
import java.util.UUID;

public record CreatePaymentClientRequest(
        UUID orderId,
        BigDecimal amount,
        String orderNumber,
        String customerEmail,
        String paymentMode
) {
    public static CreatePaymentClientRequest from(Order order) {
        return new CreatePaymentClientRequest(
                order.getId(),
                order.getTotalAmount(),
                order.getOrderNumber(),
                order.getCustomerEmail() != null ? order.getCustomerEmail() : "",
                order.getPaymentMethod() == PaymentMethod.SBP ? "SBP" : "CARD"
        );
    }
}
