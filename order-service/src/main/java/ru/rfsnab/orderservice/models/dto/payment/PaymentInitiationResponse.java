package ru.rfsnab.orderservice.models.dto.payment;

import ru.rfsnab.orderservice.models.entity.enums.OrderStatus;
import ru.rfsnab.orderservice.models.entity.enums.PaymentMethod;

public record PaymentInitiationResponse(
        String paymentLink,
        PaymentMethod paymentMode,
        OrderStatus orderStatus
) {}
