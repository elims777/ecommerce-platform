package ru.rfsnab.orderservice.models.dto.payment;

public record PaymentMethodSettingsDto(boolean sbpEnabled, boolean cardEnabled) {
}
